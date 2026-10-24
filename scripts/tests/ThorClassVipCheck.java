import mobiarmy.server.*;import mobiarmy.io.Message;import java.sql.*;import java.io.*;import java.net.Socket;import java.util.*;
public class ThorClassVipCheck {
 static int C,ID,H; static int n;static List<Message> sent=new ArrayList<>();
 static class Capture extends Session {Capture()throws Exception{super(new Socket(),-1);}public void sendMessage(Message m){sent.add(new Message(m.getCommand(),m.getData()));}}
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);n++;System.out.println("PASS "+s);}
 public static void main(String[] args)throws Exception{C=Integer.parseInt(args[1]); ID=30100+C; H=new int[]{26,19,23,24,25,21,21,22,22,19}[C]; int id=-1;String name="thorcheck_"+Long.toHexString(System.nanoTime());try{
 Glass.loadGlass();Equip.loadEquip();ShopEquipment.loadShopEquipment();Item.loadItem();LinhTinh.loadLinhTinh();Mission.loadMession();Exp.loadExp();
 Equip catalog=Equip.get((byte)C,(short)ID);check(catalog.vip==1&&catalog.type==1&&catalog.luong==50&&catalog.date==7,"VIP catalog 50 gold seven days");check(Arrays.equals(catalog.data,new short[]{ThorCostume.WEAPON,(short)ID,ThorCostume.ARMOR,-1,C==5?-1:ThorCostume.CAPE}),"Thor outfit pieces (ThorCostume) and unique VIP appearance marker");
 try(Connection c=Server.dbManager.openConnection();PreparedStatement p=c.prepareStatement("INSERT INTO user(username,password) VALUES (?,?)",Statement.RETURN_GENERATED_KEYS)){p.setString(1,name);p.setString(2,"disabled-test-account");p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){r.next();id=r.getInt(1);}}
 User u=new User(id,name);u.selectGlass=(byte)C;u.glass().isOpen=true;u.luong=49;
 java.lang.reflect.Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);Capture s=(Capture)((sun.misc.Unsafe)f.get(null)).allocateInstance(Capture.class);s.user=u;s.sessionHandler=new SessionHandler(s);s.controlHandler=new ControlHandler(s);u.session=s;
 s.sessionHandler.equipShop((byte)C);int index=-1;for(int i=0;i<u.generates.size();i++)if(u.generates.get(i).id==ID)index=i;check(index>=0,"Thor listed in matching class shop");
 u.buyEquip(index,(byte)1);check(u.equips.isEmpty()&&u.luong==49,"insufficient funds rejected");u.luong=100;u.buyEquip(index,(byte)1);check(u.equips.size()==1&&u.luong==50,"purchase charges 50 and grants VIP");Equip owned=u.equips.get(0);
 u.setEquipVip((byte)1,65535);check(!owned.isUse,"unowned VIP rejected");u.selectGlass=8;u.setEquipVip((byte)1,owned.dbKey);check(!owned.isUse,"foreign class rejected");u.selectGlass=(byte)C;u.setEquipVip((byte)1,owned.dbKey);check(owned.isUse&&u.getData()[1]==ID,"equipped VIP broadcasts class costume marker");
 SessionManager.saveUser(u);SessionManager.loadUser();User loaded=SessionManager.findUserById(id);loaded.glass().updateAll();check(loaded.luong==50&&loaded.getData()[1]==ID,"ownership equipped state and balance survive reload");loaded.setEquipVip((byte)0,owned.dbKey);check(loaded.getData()[1]!=ID,"unequip restores normal appearance");
 sent.clear();s.sessionHandler.dataInfo(14,13);DataInputStream in=sent.get(0).reader();check(in.readByte()==4&&in.readByte()==14,"new metadata version");byte[] bytes=new byte[in.readInt()];in.readFully(bytes);java.nio.file.Files.write(java.nio.file.Path.of(args[0]),bytes);
 System.out.println("THOR CLASS "+C+" PASSED: "+n);
 }finally{if(id!=-1)try(Connection c=Server.dbManager.openConnection()){c.setAutoCommit(false);for(String table:new String[]{"user_","user_glass","user_item","user_equip","user_linhtinh","user_mission","user_friend"})try(PreparedStatement p=c.prepareStatement("DELETE FROM "+table+" WHERE user_id=?")){p.setInt(1,id);p.executeUpdate();}try(PreparedStatement p=c.prepareStatement("DELETE FROM user WHERE id=? AND username=?")){p.setInt(1,id);p.setString(2,name);p.executeUpdate();}c.commit();}}
 }
}
