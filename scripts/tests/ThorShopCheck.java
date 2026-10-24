import mobiarmy.server.*;
import mobiarmy.io.Message;
import java.io.*;
import java.net.Socket;
import java.sql.*;
import java.util.*;
public class ThorShopCheck {
 static int checks; static final List<Message> sent=new ArrayList<>();
 static class Capture extends Session { Capture() throws Exception {super(new Socket(),-1);} public void sendMessage(Message m){sent.add(new Message(m.getCommand(),m.getData()));} }
 static void check(boolean b,String text){if(!b)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
 static void request(Capture session,Message m) throws Exception { session.controlHandler.handleControlMessage(new Message(m.getCommand(),m.getData())); }
 static void buy(Capture s,int index,int currency)throws Exception{Message m=new Message(104);m.writer().writeByte(0);m.writer().writeShort(index);m.writer().writeByte(currency);request(s,m);}
 static void equip(Capture s,int key)throws Exception{Message m=new Message(102);for(int i=0;i<5;i++)m.writer().writeInt(i==2?key:-1);request(s,m);}
 static void bonus(User u,int[] base){Equip skin=u.equips.get(0);byte[] percent=skin.inv_percen;skin.inv_percen=new byte[5];check(Arrays.equals(base,u.glass().createAbility()),"no flat bonus: Thor only adds its own catalog percent");skin.inv_percen=percent;check(Arrays.equals(base,u.glass().createAbility(true)),"balanced rooms count Thor as a costume only");}
 public static void main(String[] args)throws Exception{
  int id=-1;String name="thorcheck_"+Long.toHexString(System.nanoTime());
  try {
   Glass.loadGlass();ThorSkin.ensureCatalog();ThorSkin.ensureCatalog();ThorCostume.ensureCatalog();CostumeBullets.ensureCatalog();Equip.loadEquip();ShopEquipment.loadShopEquipment();Item.loadItem();LinhTinh.loadLinhTinh();Mission.loadMession();Exp.loadExp();
   try(Connection c=Server.dbManager.openConnection();PreparedStatement p=c.prepareStatement("INSERT INTO user(username,password) VALUES (?,?)",Statement.RETURN_GENERATED_KEYS)) {p.setString(1,name);p.setString(2,"disabled-test-account");p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){r.next();id=r.getInt(1);}}
   User u=new User(id,name);u.selectGlass=8;u.glass().isOpen=true;u.luong=999;u.xu=999999;
   java.lang.reflect.Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);sun.misc.Unsafe unsafe=(sun.misc.Unsafe)f.get(null);
   Capture s=(Capture)unsafe.allocateInstance(Capture.class);s.user=u;s.sessionHandler=new SessionHandler(s);s.controlHandler=new ControlHandler(s);u.session=s;
   request(s,new Message(103));
   check(ThorSkin.is(u.generates.get(0)),"Thor first in Apache shop");
   Message shop=sent.stream().filter(m->m.getCommand()==103).findFirst().get();byte[] shopBytes=shop.reader().readAllBytes();java.nio.file.Files.write(java.nio.file.Path.of(args[0]+".shop"),shopBytes);DataInputStream r=new DataInputStream(new ByteArrayInputStream(shopBytes));check(r.readShort()>0,"shop nonempty");
   check(r.readByte()==8 && r.readByte()==2 && r.readShort()==30000 && r.readUTF().equals("Skin Thor") && r.readInt()==-1 && r.readInt()==1000 && r.readByte()==127,"shop packet ID/slot/name/1000 gold/permanent marker");
   int[] base=u.glass().createAbility();
   buy(s,0,1);check(u.luong==999 && u.equips.isEmpty(),"insufficient gold rejected without mutation");
   buy(s,0,0);check(u.xu==999999 && u.equips.isEmpty(),"xu purchase rejected");
   u.luong=2000;buy(s,-1,1);buy(s,9999,1);buy(s,0,2);check(u.luong==2000 && u.equips.isEmpty(),"invalid index/currency rejected");
   equip(s,12345);check(Arrays.equals(base,u.glass().createAbility()),"unowned equipment rejected");
   buy(s,0,1);check(u.luong==1000 && u.equips.size()==1 && ThorSkin.is(u.equips.get(0)),"purchase charges exactly 1000 gold and grants one skin");
   java.nio.file.Files.write(java.nio.file.Path.of(args[0]+".purchase"),sent.stream().filter(m->m.getCommand()==104).findFirst().get().reader().readAllBytes());check(Arrays.equals(base,u.glass().createAbility()),"owned but unequipped gives no bonus");
   buy(s,0,1);check(u.luong==1000 && u.equips.size()==1,"duplicate purchase rejected without charge");
   Equip skin=u.equips.get(0);skin.renewalDate=0;check(skin.date()==127,"permanent even with old purchase date");
   u.selectGlass=0;equip(s,skin.dbKey);check(!skin.isUse,"cannot equip Apache skin on another class");u.selectGlass=8;
   equip(s,skin.dbKey);check(skin.isUse && u.getData()[2]==30000,"equip publishes skin ID in normal appearance data");bonus(u,base);
   int[] attrs=u.glass().createAbility();mobiarmy.war.Player combat=new mobiarmy.war.Player(null,u,name,8,1,attrs[0],attrs[1],attrs[2],attrs[3],attrs[4],24,24,null);
   check(combat.hpMax==attrs[0] && combat.att==attrs[1] && combat.luck==attrs[3],"combat receives the stats createAbility gives");
   SessionManager.saveUser(u);SessionManager.loadUser();User loaded=SessionManager.findUserById(id);loaded.glass().updateAll();
   check(loaded.luong==1000 && loaded.equips.size()==1 && loaded.equips.get(0).isUse && loaded.getData()[2]==30000,"DB reload preserves wallet, ownership, equipped skin");bonus(loaded,base);
   loaded.session=s;s.user=loaded;equip(s,-1);check(!loaded.equips.get(0).isUse && Arrays.equals(base,loaded.glass().createAbility()) && loaded.getData()[2]!=30000,"unequip removes appearance and every bonus");
   SessionManager.saveUser(loaded);SessionManager.loadUser();User reloaded=SessionManager.findUserById(id);check(!reloaded.equips.get(0).isUse && reloaded.luong==1000,"unequipped state persists");
   // Export real metadata packet for the client-side decoder test.
   sent.clear();s.sessionHandler.dataInfo(13,12);Message metadata=sent.get(0);DataInputStream in=metadata.reader();check(in.readByte()==4 && in.readByte()==13,"catalog cache version bumped");int n=in.readInt();byte[] payload=new byte[n];in.readFully(payload);java.nio.file.Files.write(java.nio.file.Path.of(args[0]),payload);
   System.out.println("THOR SHOP PASSED: "+checks+" checks, actual command handlers and SQL persistence");
  } finally {
   if(id!=-1)try(Connection c=Server.dbManager.openConnection()) {c.setAutoCommit(false);for(String table:new String[]{"user_","user_glass","user_item","user_equip","user_linhtinh","user_mission","user_friend"})try(PreparedStatement p=c.prepareStatement("DELETE FROM "+table+" WHERE user_id=?")){p.setInt(1,id);p.executeUpdate();}try(PreparedStatement p=c.prepareStatement("DELETE FROM user WHERE id=? AND username=?")){p.setInt(1,id);p.setString(2,name);p.executeUpdate();}c.commit();}
  }
 }
}
