package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/**
 * The page the phone opens from the QR. Local network only, plain HTTP, with a random code in the address.
 * It runs only while it's being used: it stops by itself after {@link #IDLE_MS} without a request.
 */
final class PhoneServer {
    static final int PORT = 8765;
    private static final long IDLE_MS = 30 * 60_000L;
    private static final int MAX_BODY = 64 * 1024;
    private static final int READ_TIMEOUT_MS = 2_000;
    private static final long REQUEST_DEADLINE_MS = 4_000L;
    private static final int QUEUED = 12;
    private static final int WORKERS = 6;

    /** What the page changes. Always called on the main thread. */
    interface Host {
        List<PhoneProtocol.Tile> tiles();
        void setOrder(List<String> ids);
        void setHidden(String id, boolean hidden);
        void rename(String id, String name);
    }

    private static PhoneServer running;
    private static Host host;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Context app;
    private final String token;
    private final ServerSocket socket;
    private volatile long lastUsed = System.currentTimeMillis();
    private volatile long pageSeen;
    private boolean keyboardHidden;
    private final java.util.concurrent.ThreadPoolExecutor workers = new java.util.concurrent.ThreadPoolExecutor(WORKERS, WORKERS, 0L,
            java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(QUEUED));
    private final PhoneProtocol.Once sends = new PhoneProtocol.Once();
    private boolean stopped;

    static void setHost(Host h) {
        host = h;
    }

    /** Starts the server if needed and returns the code of the running one, or null if the port can't be opened. */
    static synchronized String start(Context context) {
        if (running != null) {
            running.lastUsed = System.currentTimeMillis();
            return running.token;
        }
        try {
            byte[] raw = new byte[12];
            new SecureRandom().nextBytes(raw);
            StringBuilder hex = new StringBuilder();
            for (byte b : raw) hex.append(String.format("%02x", b));
            running = new PhoneServer(context.getApplicationContext(), hex.toString(), new ServerSocket(PORT));
            new Thread(running::acceptLoop, "phone-server").start();
            return running.token;
        } catch (IOException e) {
            return null;
        }
    }

    private PhoneServer(Context app, String token, ServerSocket socket) throws IOException {
        this.app = app;
        this.token = token;
        this.socket = socket;
        socket.setSoTimeout(1_000);
    }

    /** Amazon's on-screen keyboard stays away while the phone is the keyboard. */
    private synchronized void stop() {
        updateKeyboard(false);
        stopped = true;   // a request still being served can't hide the keyboard again with nobody left to undo it
    }

    private synchronized void updateKeyboard(boolean hide) {
        if (stopped || hide == keyboardHidden) return;
        keyboardHidden = hide;
        TvAccessibilityService.hideKeyboard(hide);
    }

    private void serve(Socket connection) {
        try (Socket client = connection) {
            client.setSoTimeout(READ_TIMEOUT_MS);
            handle(client);
        } catch (IOException | RuntimeException ignored) {   // a bad request must not stop the server
        }
    }

    private void acceptLoop() {
        while (true) {
            synchronized (PhoneServer.class) {   // start() can't hand out a server that is on its way out
                if (System.currentTimeMillis() - lastUsed >= IDLE_MS) {
                    running = null;
                    break;
                }
            }
            try {
                Socket client = socket.accept();
                try {
                    workers.execute(() -> serve(client));   // a phone that just woke up leaves silent connections behind
                } catch (java.util.concurrent.RejectedExecutionException full) {
                    client.close();
                }
            } catch (SocketTimeoutException idle) {
                // checks the idle limit
            } catch (IOException | RuntimeException ignored) {   // a bad request must not stop the server
            }
            updateKeyboard(PhoneProtocol.pageOpen(pageSeen, System.currentTimeMillis()));
        }
        stop();
        workers.shutdown();
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private static String readLine(InputStream in, long deadline) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) >= 0 && c != '\n') {
            if (line.size() > 8192 || System.currentTimeMillis() > deadline) throw new IOException("bad request");
            if (c != '\r') line.write(c);
        }
        return line.toString("UTF-8");
    }

    private void handle(Socket client) throws IOException {
        InputStream in = client.getInputStream();
        long deadline = System.currentTimeMillis() + REQUEST_DEADLINE_MS;   // however slowly the bytes trickle in
        String[] request = readLine(in, deadline).split(" ");
        if (request.length < 2) return;
        if (!PhoneProtocol.tokenOk(request[1], token)) {
            respond(client.getOutputStream(), "403 Forbidden", "text/plain", "code required".getBytes(StandardCharsets.UTF_8));
            return;
        }
        int length = 0;
        for (String h = readLine(in, deadline); !h.isEmpty(); h = readLine(in, deadline)) {
            if (h.toLowerCase().startsWith("content-length:")) length = Integer.parseInt(h.substring(15).trim());
        }
        if (length < 0 || length > MAX_BODY) return;
        byte[] body = new byte[length];
        for (int got = 0; got < length; ) {
            int n = in.read(body, got, length - got);
            if (n < 0 || System.currentTimeMillis() > deadline) return;
            got += n;
        }
        OutputStream out = client.getOutputStream();
        lastUsed = pageSeen = System.currentTimeMillis();
        updateKeyboard(true);
        route(out, request[0], PhoneProtocol.path(request[1]), PhoneProtocol.form(new String(body, StandardCharsets.UTF_8)));
    }

    private void route(OutputStream out, String method, String path, Map<String, String> f) throws IOException {
        if (method.equals("GET") && path.equals("/")) {
            respond(out, "200 OK", "text/html; charset=utf-8", asset("phone.html"));
        } else if (method.equals("GET") && path.equals("/api/tiles")) {
            json(out, onMain(() -> PhoneProtocol.tilesJson(host.tiles())));
        } else if (method.equals("GET") && path.equals("/api/ping")) {
            json(out, "{\"ok\":true}");
        } else if (method.equals("POST") && path.equals("/api/text")) {
            json(out, sends.run(f.getOrDefault("id", ""),
                    () -> TvAccessibilityService.typeText(f.getOrDefault("text", ""), "1".equals(f.get("enter")))));
        } else if (method.equals("POST") && path.equals("/api/order")) {
            List<String> ids = new ArrayList<>(Arrays.asList(f.getOrDefault("ids", "").split("\n")));
            change(out, () -> host.setOrder(ids));
        } else if (method.equals("POST") && path.equals("/api/hide")) {
            change(out, () -> host.setHidden(f.getOrDefault("id", ""), "1".equals(f.get("hidden"))));
        } else if (method.equals("POST") && path.equals("/api/rename")) {
            change(out, () -> host.rename(f.getOrDefault("id", ""), f.getOrDefault("name", "")));
        } else {
            respond(out, "404 Not Found", "text/plain", new byte[0]);
        }
    }

    private void change(OutputStream out, Runnable action) throws IOException {
        onMain(() -> {
            action.run();
            return "";
        });
        json(out, "{\"ok\":true}");
    }

    private <T> T onMain(Callable<T> work) throws IOException {
        if (host == null) throw new IOException("launcher not ready");
        FutureTask<T> task = new FutureTask<>(work);
        main.post(task);
        try {
            return task.get();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private byte[] asset(String name) throws IOException {
        try (InputStream in = app.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static void json(OutputStream out, String body) throws IOException {
        respond(out, "200 OK", "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(OutputStream out, String status, String type, byte[] body) throws IOException {
        out.write(("HTTP/1.1 " + status + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }
}
