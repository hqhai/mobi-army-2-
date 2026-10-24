package mobiarmy.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import mobiarmy.io.Message;
import mobiarmy.war.MapData;
import mobiarmy.war.Player;
import mobiarmy.war.Room;
import mobiarmy.war.RoomInfo;

/**
 *
 * @author Văn Tú
 */
public class Server {
    
    private int port;
    private volatile boolean is_start;
    private ServerSocket server;
    private Thread updateThread;
    private long nextSaveAt = System.currentTimeMillis() + 30000L;
    private static boolean saveRequested;
    public static final ArrayList<Command> CMD = new ArrayList<>();
    // Admin edits to character/equipment data bump this; clients re-download cached dataInfo when the version differs.
    public static volatile int dataRevision = 0;
    
    public static class Command  {
        
        public int cmd;
        public Object[] object;

        public Command(int cmd, Object... object) {
            this.cmd = cmd;
            this.object = object;
        }
        
    }
    
    public static final DBManager dbManager = new DBManager("jdbc:mysql://localhost:3306/army", "root", "");
    
    public Server(int port) {
        this.port = port;
        this.is_start = false;
    }
    
    public void start() {
        if (!this.is_start) {
            try {
                Glass.loadGlass();
                Map.loadMap();
                MapBoss.loadMapBoss();
                ThorSkin.ensureCatalog();
                PhotonSkin.ensureCatalog();
                ThorCostume.ensureCatalog();
                CostumeBullets.ensureCatalog();
                Equip.loadEquip();
                Item.loadItem();
                LinhTinh.loadLinhTinh();
                ShopEquipment.loadShopEquipment();
                ShopLinhTinh.loadShopLinhTinh();
                Exp.loadExp();
                Caption.loadCaption();
                Mission.loadMession();
                Room.loadRoom();
                RoomInfo.loadRoomInfo();
                SessionManager.loadUser();
            } catch(SQLException | IOException ex) {
                ex.printStackTrace();
            }
            try {
                GameData.loadHole();
                GameData.loadMapIcon();
                GameData.loadLayer();
            } catch(IOException ex) {
                ex.printStackTrace();
            }
            updateThread = new Thread(() -> {
                while (is_start) {
                    update();
                    try {
                        TimeUnit.MILLISECONDS.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            
            Bot.generateBot();
            
            try {
                try {
                    System.out.println("Start server port:"+this.port);
                    this.server = new ServerSocket(this.port);
                    this.is_start = true;
                    updateThread.start();
                    for (int i = 0;this.is_start; i++) {
                        Session session = new Session(this.server.accept(), i);
                        SessionManager.addSession(session);
                        session.start();
                    }
                } catch (IOException ex) {
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    private synchronized void update() {
        if (!this.is_start) return;
        ArrayList<Command> commands;
        synchronized (CMD) {
            commands = new ArrayList<>(CMD);
        }
        for (Command command : commands) {
            if (command.cmd == 0) {
                Session session = (Session) command.object[0];
                Message message = (Message) command.object[1];
                if (session.user != null && session.user.lock && message.getCommand() != -4
                        && ControlHandler.syncCommands.contains((int) message.getCommand())) {
                    continue;
                }
            }
            try {
                switch (command.cmd) {
                    case 0 ->  {
                        Session session = (Session) command.object[0];
                        Message message = (Message) command.object[1];
                        if (session.user != null) {
                            session.user.update();
                        }
                        if (session.connected) {
                            session.controlHandler.handleControlMessage(message);
                        }
                    }
                    case 1 -> {
                        MapData mapData = (MapData) command.object[0];
                        //Giải phóng hành động
                        mapData.roomWait.lock = false;
                        for (Player player : mapData.players) {
                            if (player != null && player.user != null) {
                                player.user.lock = false;
                            }
                        }
                    }
                    case 2 -> ((FutureTask<?>) command.object[0]).run();
                }
            } catch (IOException | RuntimeException e) {
                if (command.cmd == 0) {
                    Session session = (Session) command.object[0];
                    Message message = (Message) command.object[1];
                    System.err.printf("Rejected command %d from session %d (%s)%n",
                            message.getCommand(), session.id, e.getClass().getSimpleName());
                    try {
                        session.disconnect();
                    } catch (IOException | RuntimeException closeError) {
                        closeError.printStackTrace();
                    }
                } else {
                    e.printStackTrace();
                }
            } finally {
                removeCommand(command);
            }
        }
        RoomInfo.update();
        Bot.updateBot();
        if (saveRequested || System.currentTimeMillis() >= nextSaveAt) {
            saveRequested = false;
            nextSaveAt = System.currentTimeMillis() + 30000L;
            try {
                SessionManager.saveUsers();
            } catch (SQLException ex) {
                System.err.println("Failed to save users; retrying at the next periodic checkpoint.");
                ex.printStackTrace();
            }
        }
    }
    
    public static void requestSave() {
        saveRequested = true;
    }

    public synchronized boolean stop() {
        if (this.is_start) {
            try {
                SessionManager.saveUsers();
            } catch (SQLException ex) {
                System.err.println("Save failed; server remains running so shutdown can be retried.");
                ex.printStackTrace();
                return false;
            }
            this.is_start = false;
            try {
                this.server.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return true;
    }
    
    public boolean isRunning() {
        return is_start;
    }
    
    public static void addCommand(Session session, Message message) {
        synchronized (CMD) {
            CMD.add(new Command(0, session, message));
        }
    }
    
    public static void addCommand(int cmd, Object... object) {
        synchronized (CMD) {
            CMD.add(new Command(cmd, object));
        }
    }
    
    public static void removeCommand(Command command) {
        synchronized (CMD) {
            CMD.remove(command);
        }
    }

    /** The dataInfo version sent to clients: the code's baseline shifted by admin edits, kept within 1..127. */
    public static int dataVersion(int baseline) {
        return Math.floorMod(baseline - 1 + dataRevision, 127) + 1;
    }

    /** Runs a task on the game loop thread (where all game state changes happen) and waits for its result. */
    public static <T> T callOnGameThread(Callable<T> task, long timeoutMillis) throws Exception {
        FutureTask<T> future = new FutureTask<>(task);
        addCommand(2, future);
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(false); // never apply it later if the loop picks it up after we gave up
            throw e;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            throw e;
        }
    }

}
