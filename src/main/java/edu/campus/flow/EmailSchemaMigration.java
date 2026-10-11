package edu.campus.flow;

import java.sql.SQLException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class EmailSchemaMigration implements ApplicationRunner {
 private final JdbcTemplate db;
 EmailSchemaMigration(JdbcTemplate db){this.db=db;}

 @Override public void run(ApplicationArguments args) throws SQLException {
  var connection=db.getDataSource().getConnection();
  try(connection){
   var metadata=connection.getMetaData();boolean hasEmail=false;
   for(String table:new String[]{"users","USERS"})try(var columns=metadata.getColumns(connection.getCatalog(),null,table,null)){
    while(columns.next())if("email".equalsIgnoreCase(columns.getString("COLUMN_NAME")))hasEmail=true;
   }
   if(!hasEmail)db.execute("ALTER TABLE users ADD COLUMN email VARCHAR(254)");
   boolean hasSection=false;
   for(String table:new String[]{"users","USERS"})try(var columns=metadata.getColumns(connection.getCatalog(),null,table,null)){
    while(columns.next())if("section".equalsIgnoreCase(columns.getString("COLUMN_NAME")))hasSection=true;
   }
   if(!hasSection)db.execute("ALTER TABLE users ADD COLUMN section VARCHAR(20) DEFAULT '' NOT NULL");
   boolean hasIndex=false;
   for(String table:new String[]{"users","USERS"})try(var indexes=metadata.getIndexInfo(connection.getCatalog(),null,table,true,false)){
    while(indexes.next())if("uq_users_email".equalsIgnoreCase(indexes.getString("INDEX_NAME")))hasIndex=true;
   }
   if(!hasIndex)db.execute("CREATE UNIQUE INDEX uq_users_email ON users(email)");
  }
 }
}
