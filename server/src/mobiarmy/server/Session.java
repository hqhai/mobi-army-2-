package mobiarmy.server;

import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import mobiarmy.io.Message;

public class Session {
    
    public int                  id;
    public Socket               socket;
    public volatile boolean    connected;
    public MessageHandler       messageHandler;
    public ControlHandler       controlHandler;
    public SessionHandler       sessionHandler;
    private Thread              connectionMonitorThread;
    public User                 user;
    public Training             training;
    public String               version;

    public Session(Socket socket, int id) throws IOException {
        this.id = id;
        this.socket = socket;
        this.messageHandler = new MessageHandler(this);
        this.controlHandler = new ControlHandler(this);
        this.sessionHandler = new SessionHandler(this);
        this.connected = true;
        this.user = null;
        startConnectionMonitor();
    }

    private void startConnectionMonitor() {
        connectionMonitorThread = new Thread(() -> {
            try {
                while (connected) {
                    for (Message message = messageHandler.popMessage(); message != null; message = messageHandler.popMessage()) {
                        Server.addCommand(this, message);
                    }
                    TimeUnit.MILLISECONDS.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                requestDisconnect();
            }
        });
        connectionMonitorThread.start();
    }
    
    public void sendMessage(Message message) {
        this.messageHandler.addMessage(message);
    }

    public synchronized void requestDisconnect() {
        Server.addCommand(this, new Message(-4));
    }

    public void disconnect() throws IOException {
        if (!this.connected) return;
        this.connected = false;
        this.training = null;
        IOException closeError = null;
        try {
            messageHandler.close();
        } catch (IOException ex) {
            closeError = ex;
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ex) {
            if (closeError == null) closeError = ex;
            else closeError.addSuppressed(ex);
        }
        try {
            if (user != null) {
                user.leaveRoomWait();
                try {
                    SessionManager.saveUser(user);
                } catch (SQLException ex) {
                    System.err.println("Failed to save disconnected user " + user.id + "; periodic save will retry.");
                    ex.printStackTrace();
                }
            }
        } finally {
            if (user != null) {
                user.session = null;
                user = null;
            }
            SessionManager.removeSession(this);
        }
        if (closeError != null) throw closeError;
    }
    
    public boolean isGE(String compareVersion) {
        try {
            String[] targetParts = this.version.split("[^0-9]+");
            String[] compareParts = compareVersion.split("[^0-9]+");

            int len = Math.max(targetParts.length, compareParts.length);

            for (int i = 0; i < len; i++) {
                int targetVal = i < targetParts.length ? Integer.parseInt(targetParts[i]) : 0;
                int compareVal = i < compareParts.length ? Integer.parseInt(compareParts[i]) : 0;

                if (targetVal > compareVal) return true;
                if (targetVal < compareVal) return false;
            }
        } catch (Exception e) {
            this.requestDisconnect();
        }
        return true;
    }

    public void start() {
        messageHandler.startReadingThread();
        messageHandler.startSendingThread();
    }
}
