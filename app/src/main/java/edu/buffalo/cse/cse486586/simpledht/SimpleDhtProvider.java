package edu.buffalo.cse.cse486586.simpledht;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import android.content.*;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.telephony.TelephonyManager;
import java.net.SocketException;
import android.app.Activity;

import android.os.AsyncTask;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.webkit.WebChromeClient;
import android.widget.EditText;
import android.widget.TextView;
import android.content.ContentResolver;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import android.util.Log;
import java.net.ServerSocket;
import java.util.HashMap;

/*References:
https://developer.android.com/reference/android/database/MatrixCursor.html
https://google-developer-training.gitbooks.io/android-developer-fundamentals-course-concepts/content/en/Unit%204/101_c_sqlite_database.html
https://developer.android.com/guide/topics/providers/content-provider-creating.html
*/

import MessengerDatabase.DBHandler;

public class SimpleDhtProvider extends ContentProvider {

    ArrayList<String> chordInfo = new ArrayList<String>();
    HashMap<String, String> portInfo = new HashMap<String, String>();

    static final String TAG = SimpleDhtProvider.class.getSimpleName();
    private DBHandler myDB;

    static final int SERVER_PORT = 10000;
    static String myHash = "";
    static String myPort = "";
    static String creator = "11108";
    Node myself = null;

    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub

       // Log.d("Delete" , "Entered Delete");
        SQLiteDatabase sqlDB = myDB.getWritableDatabase();
       // Log.d("Delete" , "Selection is " + selection);

        try{
            if(!(selection.equals("@") || selection.equals("*"))){

               // Log.d("query", "Only single key is deleted");
                int count = sqlDB.delete("Tb_KeyPair","key = ?", new String[]{selection});

                if(count>0)
                    return count;

                else
                    return sendDeleteRequest(selection);
            }

            else{

                if(selection.equals("@"))
                    return deleteMyData("*");
                else
                    return sendDeleteRequest("*");
            }
        }
        catch (Exception e){
            Log.e("Delete","Something messy");
        }

        return 0;
    }

  /*  void printmykeys()
    {
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables("Tb_KeyPair");

        Cursor cursor = queryBuilder.query(myDB.getReadableDatabase(),
                new String[]{"key","value"}, null, null, null, null,
                null);

        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            Log.d("Printkeys" , " Key : " + cursor.getString(0) + " Value : " + cursor.getString(1));
        }
    } */

    private int sendDeleteRequest(String selection){

        String start_port = myself.getSuccessor_port();

        while(!(start_port.equals(myself.getRemoteport()))){

            try {

              //  Log.d("ClientTask", "Sending agreement to : " + start_port);

                Socket socket1 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(start_port));
                socket1.setSoTimeout(500);
                PrintWriter out =
                        new PrintWriter(socket1.getOutputStream(), true);


              //  Log.d(TAG, "Client: PrintWriter Created");
                out.println(selection + "###Delete");
                out.flush();

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket1.getInputStream()));

                String line = in.readLine();

                if(line!=null){

                    if(selection.equals("*")){

                        if(start_port.equals(line))
                            break;
                        start_port = line;
                    }
                    else{
                        String lines[] = line.split("###");
                        if(lines[0].equals("Fail")){
                            if(start_port.equals(lines[1])){
                                out.close();
                                socket1.close();
                                break;
                            }
                            start_port = lines[1];
                        }
                        else{
                            out.close();
                            socket1.close();
                            break;
                        }
                    }

                }
                out.close();
                socket1.close();

            } catch (UnknownHostException e) {
                Log.e(TAG, "Pratibha Alert ClientTask UnknownHostException");
            } catch (SocketTimeoutException e) {
                Log.e(TAG, "Pratibha Alert ClientTask socket time out");
            } catch (IOException e) {
                Log.e(TAG, "Pratibha Alert ClientTask socket IOException");
            }
        }

        return 0;
    }


    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub

        try {

           // Log.d("Insert" , "Start the code");

            String key = values.get("key").toString();
            String hashkey = this.genHash(key);
            String value = values.get("value").toString();

          //  Log.d("Insert" , "key : " + key);
          //  Log.d("Insert" , "hashkey : " + hashkey);
          //  Log.d("Insert" , "value : " + value);



            if(belongsToMe(hashkey)){
              //  Log.d("Insert" , "The hashkey belongs to me");
                insertIntoDB(key,value);
            }

            else{
               // Log.d("Insert" , "It is not my hashkey");
                new ClientTask(hashkey,value).executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "Forward",key);
            }

            return uri;

        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Pratibha Alert No Such Algo Exception");
            return null;
        }
    }

    private boolean valueBetween(String start, String end, String value) {
        return (value.compareTo(start) > 0 && end.compareTo(value) > 0);
    }

    private boolean belongsToMe(String hashkey){

      //  Log.d("BelongsToMe" , "Check if key is yours");
      //  Log.d("My key" , myself.getHash());
     //   Log.d("My pre" , myself.getPredecessor());
     //   Log.d("My suc" , myself.getSuccessor());
     //   Log.d("hashkey" , hashkey);


        if(myself.getHash().compareTo(hashkey)==0)
            return true;

        if(myself.getPredecessor().equals(myself.getHash()))
            return true;

       // Log.d("My pre" , "compare further");

        if(myself.getHash().compareTo(myself.getPredecessor())>0){
           // Log.d("Insert", "My predecessor is less than me");

            if(valueBetween(myself.getPredecessor(),myself.getHash(),hashkey)==true){
               // Log.d("My pre" , "Pass case 1");
                return true;
            }
        }

        if (myself.getPredecessor().compareTo(myself.getHash())>0) {
           // Log.d("Insert", "My predecessor is greater than me");

            if(myself.getHash().compareTo(hashkey)>0 || hashkey.compareTo(myself.getPredecessor())>0){
               // Log.d("My pre" , "Pass case 2");
                return true;
            }
        }

       return false;
    }

    private void insertIntoDB(String key, String value)
    {
        ContentValues mContentValues = new ContentValues();
        mContentValues.put("key",key);
        mContentValues.put("value",value);

       // Log.v("db", "About to get writable database");
        SQLiteDatabase sqlDB = myDB.getWritableDatabase();
       // Log.v("db", "got writable database");

        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables("Tb_KeyPair");

        long result = sqlDB.insertWithOnConflict("Tb_KeyPair",null,mContentValues,SQLiteDatabase.CONFLICT_REPLACE);
      //  Log.d("InsertIntoDB" , "Wrote key " + key + " value : " + value);

    }

    @Override
    public boolean onCreate() {
        // TODO Auto-generated method stub

        try{
          //  Log.d("OnCreate","The system has started coding");
            myDB = new DBHandler(getContext(),null,null,1);
          //  Log.d("OnCreate","Line1");

            TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
            String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
          //  Log.d("OnCreate","Line2 : portstr " + portStr);

            myHash = this.genHash(portStr);
          //  Log.d("OnCreate","Line3 : Myhash " + myHash);

            myPort = String.valueOf((Integer.parseInt(portStr) * 2));
          //  Log.d("OnCreate","Line4 : myport " + myPort);

            try{

                myself = new Node(myPort,myHash,myHash,myHash,myPort,myPort);
                if(myPort.equals(creator)) {
                   // Log.d("OnCreate","Line6 : Its me");

                    // I am 5554 who creates everyone
                    chordInfo.add(myHash);
                    Collections.sort(chordInfo);
                    portInfo.put(myHash,myPort);
                  //  Log.d("OnCreate","Line7 : Done updating variables");
                }

                ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
                new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
            }

            catch (IOException e) {
                Log.e("On Create", "IOException Catch in the code");
            }
            catch (Exception e){
                Log.e("On Create", "Can't create a ServerSocket");
            }
        }
        catch (NoSuchAlgorithmException e){
            Log.e(TAG, "Pratibha Alert No Such Exception");
        }
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        // TODO Auto-generated method stub query method

       // Log.d("Query" , "Entered Query");

        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables("Tb_KeyPair");

       // Log.d("Query" , "Selection is " + selection);

        try{
            if(!(selection.equals("@") || selection.equals("*"))){

               // Log.d("query", "Only single key is needed");
                Cursor cursor = queryBuilder.query(myDB.getReadableDatabase(),
                        new String[]{"key","value"}, "key = ?", new String[]{selection}, null, null,
                        sortOrder);

                if((cursor!=null && cursor.getCount()>0)|| myself.getSuccessor().equals(myself.getHash())){
                   // Log.d("Query" , "I am returning the cursor");
                    return cursor;
                }
                else
                    return sendQueryRequest(selection);
            }

            else{

                Cursor cursor = getMyData("*");
                if(selection.equals("@"))
                    return cursor;

                MatrixCursor mcursor = sendQueryRequest(selection);

                if(mcursor==null || mcursor.getCount()==0)
                    return cursor;

                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    mcursor.addRow(new Object[] {cursor.getString(0),cursor.getString(1)});
                }

                return mcursor;
            }
        }
        catch (Exception e){
            return null;
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    private void SendNeighbourUpdate()
    {
      //  Log.d("SendingNeighbourUpdate","Starting the code");

      //  Log.d("SendingNeighbourUpdate","Size : " + chordInfo.size());

        for(int i=0;i<chordInfo.size();i++) {

         //   Log.d("SendingNeighbourUpdate", " Iteration : " + i);

            String pre = "", suc = "";

            if (i == 0)
                pre = chordInfo.get(chordInfo.size() - 1);
            else
                pre = chordInfo.get(i - 1);

            if (i + 1 == chordInfo.size())
                suc = chordInfo.get(0);
            else
                suc = chordInfo.get(i + 1);

         //   Log.d("SendingNeighbourUpdate","pre : " + pre + " suc : " + suc);

            if (chordInfo.get(i).equals(myself.getHash())) {
             //   Log.d("SendingNeighbourUpdate","its me");

                myself.setPredecessor(pre);
                myself.setSuccessor(suc);
                myself.setPredecessor_port(portInfo.get(pre));
                myself.setSuccessor_port(portInfo.get(suc));
            }
            else {

                try {

                //    Log.d("SendingNeighbourUpdate","Send other");
                    String remotePort = portInfo.get(chordInfo.get(i));
                //    Log.d("ClientTask", "Sending agreement to : " + remotePort);

                    Socket socket1 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort));
                    socket1.setSoTimeout(500);
                    PrintWriter out =
                            new PrintWriter(socket1.getOutputStream(), true);


                 //   Log.d(TAG, "Client: PrintWriter Created");
                    out.println(pre + "###" + portInfo.get(pre) + "###" + suc + "###" + portInfo.get(suc) + "###UpdateNeighbour");
                    out.flush();

                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket1.getInputStream()));

                    String line = in.readLine();
                    out.close();
                    socket1.close();
                 //   Log.d("SendingNeighbourUpdate","Done sending : " + i);

                } catch (UnknownHostException e) {
                    Log.e(TAG, "Pratibha Alert ClientTask UnknownHostException");
                } catch (SocketTimeoutException e) {
                    Log.e(TAG, "Pratibha Alert ClientTask socket time out");
                } catch (IOException e) {
                    Log.e(TAG, "Pratibha Alert ClientTask socket IOException");
                }
            }
        }
    }

    class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {

            if(!myself.getRemoteport().equals(creator))
            {
                try {
                 //   Log.d("Connect to owner", "Starting the code");

                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(creator));

                 //   Log.d("Connect to owner", "created socket");

                    socket.setSoTimeout(500);

                    PrintWriter out =
                            new PrintWriter(socket.getOutputStream(), true);

                    // Log.d(TAG, "Client: PrintWriter Created");
                    out.println(myself.getRemoteport() + "###" + myself.getHash() + "###" + "Creation");
                    out.flush();

                 //   Log.d("Connect to owner", "Sent message. Waiting..");

                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));

                    String line = in.readLine();

                 //   Log.d("Connect to owner", "Message arrived");
                    out.close();
                    socket.close();
                 //   Log.d("Connect to owner", "Returning back");
                }
                catch (UnknownHostException e) {
                    Log.e("Client Task", "Pratibha Alert ClientTask UnknownHostException");
                } catch (SocketTimeoutException e) {
                    Log.e("Client Task", "Pratibha Alert ClientTask socket time out");
                } catch (IOException e) {
                    Log.e("Client Task", "Pratibha Alert ClientTask socket IOException");
                }
            }

            ServerSocket serverSocket = sockets[0];
            Socket socket = null;
            try {
                while(true) {
                    //  Log.d("ServerTask", "Inside while true");
                    //  Log.d(TAG, "doInBackground: In try");
                    // Server will accept the connection from the client
                   // Log.d("ServerTask","Accepting..");

                    socket = serverSocket.accept();

                    //  Log.d(TAG, "doInBackground: Accepted");

                    // This will read the message sent on the InputStream
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));

                    // Read the message line by line
                    String line = in.readLine();

                    if(line!=null)
                    {
                        String lines[] = line.split("###");

                        if(lines.length==3)
                        {
                         //   Log.d("ServerTask","Got some data");
                            String port = lines[0];
                            String hash = lines[1];
                            String type=lines[2];

                         //   Log.d("ServerTask","Port " + port + " with hash " + hash);

                            if(type.equals("Creation"))
                            {
                             //   Log.d("ServerTask","Creation data got..");
                                chordInfo.add(hash);
                                Collections.sort(chordInfo);
                                portInfo.put(hash,port);
                                PrintWriter out =
                                        new PrintWriter(socket.getOutputStream(), true);
                                out.println("Done");
                                out.flush();
                                //SendNeighbourUpdate();
                                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "Neighbour");
                             //   Log.d("ServerTask","Creation Done..");
                            }
                        }
                        else if(lines.length==5)
                        {
                            if(lines[lines.length-1].equals("UpdateNeighbour")){

                                UpdateNeighbourInfo(lines);
                                PrintWriter out =
                                        new PrintWriter(socket.getOutputStream(), true);
                                out.println("UpdatedNeighbour");
                                out.flush();
                            }
                        }

                        if(lines.length==4){
                            if(lines[lines.length-1].equals("Forward")) {

                              //  Log.d("Server Task " , "Message to check whether key is yours");

                                if(belongsToMe(lines[0])){
                                    insertIntoDB(lines[2],lines[1]);
                                    PrintWriter out =
                                            new PrintWriter(socket.getOutputStream(), true);
                                    out.println("InsertedValue");
                                    out.flush();
                                }
                                else{
                                    PrintWriter out =
                                            new PrintWriter(socket.getOutputStream(), true);
                                    out.println("InsertedValue");
                                    out.flush();

                                    new ClientTask(lines[0],lines[1]).executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "Forward",lines[2]);
                                }
                            }
                        }
                        else if(lines.length==2){

                            if(lines[1].equals("Request")){

                                Cursor cursor = null;
                                String result="";

                                if(lines[0].equals("*")){
                                    result = myself.getSuccessor_port();
                                    cursor = getMyData("*");
                                    result = result + convertCursorToString(cursor);
                                }
                                else{
                                    cursor = getMyData(lines[0]);
                                    if(cursor==null || cursor.getCount()==0)
                                        result = "Fail###" + myself.getSuccessor_port();
                                    else
                                        result = "Success" + convertCursorToString(cursor);
                                }
                                PrintWriter out =
                                        new PrintWriter(socket.getOutputStream(), true);
                                out.println(result);
                                out.flush();
                            }

                            else if(lines[1].equals("Delete")){

                                String result="";
                                if(lines[0].equals("*")){
                                    result = myself.getSuccessor_port();
                                    deleteMyData("*");
                                }
                                else{
                                    int count = deleteMyData(lines[0]);
                                    if(count>0)
                                        result = "Success";
                                    else
                                        result = "Fail###" + myself.getSuccessor_port();
                                }
                                PrintWriter out =
                                        new PrintWriter(socket.getOutputStream(), true);
                                out.println(result);
                                out.flush();
                            }



                        }
                    }
                    // Log.d("ServerTask", "Line read " + line);
                }
            } catch (SocketTimeoutException e) {
                Log.e("Server Task", "Alert Time Out Exception Catch in the code");
            } catch (IOException e) {
                Log.e("Server Task", "Alert IOException Catch in the code");
            }
            catch (Exception e) {
                e.printStackTrace();
                Log.e("Server Task", "Alert Exception Catch in the code");
            }
            return null;
        }
    }

    private Cursor getMyData(String parameter)
    {
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables("Tb_KeyPair");
        Cursor cursor=null;

        if(parameter.equals("*")){
            cursor = queryBuilder.query(myDB.getReadableDatabase(),
                    new String[]{"key","value"}, null, null, null, null,null);
        }
        else{
            cursor = queryBuilder.query(myDB.getReadableDatabase(),
                    new String[]{"key","value"}, "key =?", new String[]{parameter}, null, null,null);
        }
        return cursor;
    }

    private int deleteMyData(String parameter){

        SQLiteDatabase sqlDB = myDB.getWritableDatabase();

        if(parameter.equals("*")){
            return sqlDB.delete("Tb_KeyPair",null, null);
        }
        else{
            return sqlDB.delete("Tb_KeyPair","key = ?", new String[]{parameter});
        }
    }


    private String convertCursorToString(Cursor cursor)
    {
        if(cursor==null || cursor.getCount()==0)
            return "";

        String result = "";

        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {

            result = result + "###";
            result = result + cursor.getString(0) + "$$$" + cursor.getString(1);
        }
        return result;
    }


    class ClientTask extends AsyncTask<String, Void, Void> {

        String port,hash,portToSend, key, value;

        ClientTask(String port,String hash, String portToSend)
        {
            this.port=port;
            this.hash=hash;
            this.portToSend = portToSend;
        }
        ClientTask(){
        }

        ClientTask(String key, String value){
            this.key = key;
            this.value = value;
        }

        @Override
        protected Void doInBackground(String... msgs) {
            try
            {
                // Log.d("ClientTask", "Starting the code");

                String msgType = msgs[0];

                // Log.d("ClientTask", "Message Type " + msgType);

                if(msgType.equals("Creation")) {
                    Socket socket =  new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(portToSend));
                    socket.setSoTimeout(500);

                    PrintWriter out =
                            new PrintWriter(socket.getOutputStream(), true);

                    // Log.d(TAG, "Client: PrintWriter Created");
                    out.println(port + "###" + hash + "###" + "Creation");
                    out.flush();

                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));

                    String line = in.readLine();

                    if (line != null) {

                        String lines[] = line.split("###");
                      //  Log.d("After creation", "Lines length " + lines.length);

                        if(lines[lines.length-1].equals("UpdateNeighbour")){
                            UpdateNeighbourInfo(lines);
                            out.println("UpdatedNeighbour");
                            out.flush();
                        }
                    }
                    out.close();
                    socket.close();
                }
                else if(msgType.equals("Neighbour")){
                  //  Log.d("ServerTask","Sending Updates to everyone..");
                    SendNeighbourUpdate();
                  //  Log.d("ServerTask","Done sending updates");
                }

                else if(msgType.equals("Forward")){

                 //   Log.d("Client Task" , "Forward Function");

                    String actual_key = msgs[1];

                 //   Log.d("Insert" , "key : " + key);
                 //   Log.d("Insert" , "value : " + value);
                 //   Log.d("Insert" , "actual key : " + actual_key);

                 //   Log.d("Insert" , "Successor : " + myself.getSuccessor_port());

                    Socket socket =  new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(myself.getSuccessor_port()));

                    socket.setSoTimeout(500);

                    PrintWriter out =
                            new PrintWriter(socket.getOutputStream(), true);

                 //   Log.d(TAG, "Client: PrintWriter Created");

                    out.println(key + "###" + value + "###" + actual_key + "###Forward");
                    out.flush();

                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));

                    String line = in.readLine();
                 //   Log.d("Insert","Got reply from successor : " + line);
                    out.close();
                    socket.close();
                }
            }
            catch (UnknownHostException e) {
                Log.e("Client Task", "Pratibha Alert ClientTask UnknownHostException");
            } catch (SocketTimeoutException e) {
                Log.e("Client Task", "Pratibha Alert ClientTask socket time out");
            } catch (IOException e) {
                Log.e("Client Task", "Pratibha Alert ClientTask socket IOException");
            }

            finally {
                return null;
            }
        }
    }

    private void UpdateNeighbourInfo(String lines[]){

        myself.setPredecessor(lines[0]);
        myself.setPredecessor_port(lines[1]);
        myself.setSuccessor(lines[2]);
        myself.setSuccessor_port(lines[3]);
    }

    private MatrixCursor sendQueryRequest(String selection){

      //  Log.d("SendQueryRequest" , "key is not with me");
        MatrixCursor temp_cursor=new MatrixCursor(new String[] {"key","value"});
        String start_port = myself.getSuccessor_port();

        while(!(start_port.equals(myself.getRemoteport()))){

            try {

             //   Log.d("ClientTask", "Sending agreement to : " + start_port);

                Socket socket1 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(start_port));
                socket1.setSoTimeout(500);
                PrintWriter out =
                        new PrintWriter(socket1.getOutputStream(), true);


              //  Log.d(TAG, "Client: PrintWriter Created");
                out.println(selection + "###Request");
                out.flush();

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket1.getInputStream()));

                String line = in.readLine();

                if(line!=null){
                  //  Log.d("SendQueryRequest", "Got reply");

                    String lines[] = line.split("###");

                    if(selection.equals("*")){
                     //   Log.d("SendQueryRequest", "reply for *");

                        for(int i=1;i<lines.length;i++){

                            String values[] = lines[i].split("\\$\\$\\$");
                            temp_cursor.addRow(new Object[]{values[0],values[1]});
                        }

                        if(start_port.equals(lines[0]))
                            break;
                        start_port = lines[0];
                    }
                    else{
                      //  Log.d("SendQueryRequest", "reply for single");
                        if(lines[0].equals("Fail")){
                         //   Log.d("SendQueryRequest", "Fail");
                            if(start_port.equals(lines[1])){
                                out.close();
                                socket1.close();
                                break;
                            }
                            start_port = lines[1];
                         //   Log.d("SendQueryRequest", "continue sending");
                        }
                        else{
                          //  Log.d("SendQueryRequest", "Success");
                            String values[] = lines[1].split("\\$\\$\\$");
                          //  Log.d("SendQueryRequest", "key  : "+ values[0]);
                          //  Log.d("SendQueryRequest", "value  : "+ values[1]);

                            temp_cursor.addRow(new Object[]{values[0],values[1]});
                            out.close();
                            socket1.close();
                          //  Log.d("SendQueryRequest", "About to break");

                            break;
                        }
                    }

                }
                out.close();
                socket1.close();

            } catch (UnknownHostException e) {
                Log.e(TAG, "Pratibha Alert ClientTask UnknownHostException");
            } catch (SocketTimeoutException e) {
                Log.e(TAG, "Pratibha Alert ClientTask socket time out");
            } catch (IOException e) {
                Log.e(TAG, "Pratibha Alert ClientTask socket IOException");
            }
        }

        if(temp_cursor.getCount()==0)
            return null;

        return  temp_cursor;
    }
}