import mobiarmy.server.*;import java.sql.*;import java.nio.file.*;
public class ThorCatalogInstall {
 public static void main(String[] args)throws Exception {
  try(Connection c=Server.dbManager.openConnection()){
   for(String table:new String[]{"equip","shop_equipment"})try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT id,glassID,name FROM "+table+" WHERE id BETWEEN 30100 AND 30109")){while(r.next())if(r.getInt(1)!=30100+r.getInt(2)||!r.getString(3).startsWith("Thor "))throw new IllegalStateException("Reserved ID occupied");}
   try(Statement s=c.createStatement()){for(String sql:Files.readString(Path.of(args[0])).split(";"))if(!sql.isBlank())s.execute(sql);}
  }
  System.out.println("Thor catalog migration applied");
 }
}
