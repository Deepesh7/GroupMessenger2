package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.concurrent.PriorityBlockingQueue;

import static android.content.ContentValues.TAG;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    static final String[] REMOTE_PORTS = new String[] {"11108","11112","11116","11120","11124"};

    public static final int SERVER_PORT = 10000;

    static int num = 0;

    static double self_num = 0.0;

    static int seq_num = 0;

    public volatile int failed_port = -1;

    static int ctr = 0;

    public int file_key = 0;

    private final Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");

    public ArrayList<Data> msgs_array = new ArrayList<Data>();

    public HashMap<String, Data> hashMap = new HashMap<String, Data>();

    PriorityBlockingQueue<Data> pq = new PriorityBlockingQueue<Data>(25, new Comparator<Data>(){
        //@Override
        public int compare(Data f, Data s){
            if(f.accepted_num > s.accepted_num){
                return 1;
            }
            else{
                return -1;
            }
        }
    });


    class Data {
        double accepted_num;
        String msg;
        boolean to_deliver = false;
        int port;
        Data(){

        }
        Data(String msg, double accepted_num, int port){
            this.msg = msg;
            this.accepted_num = accepted_num;
            this.port = port;
        }

        void addMsg(String msg){
            this.msg = msg;
        }

        void addAN(double accepted_num){
            this.accepted_num = accepted_num;
        }

        void addPort(int port) { this.port = port; }

        void updateAN(double accepted_num){
            this.accepted_num = accepted_num;
            this.to_deliver = true;
        }

        @Override
        public String toString() {
            return msg + ": "+ port + ": " + accepted_num + " : "+to_deliver;
        }
    }

    public class DataComparator implements Comparator<Data>{

        @Override
        public int compare(Data lhs, Data rhs) {
            if(lhs.accepted_num > rhs.accepted_num){
                return 1;
            }
            else{
                return -1;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        TelephonyManager tel = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        int portStr = Integer.parseInt(tel.getLine1Number().substring(tel.getLine1Number().length() - 4));

        switch (portStr) {
            case 5554:
                tv.setText("1");
                self_num = 0.1;
                break;
            case 5556:
                tv.setText("2");
                self_num = 0.2;
                break;
            case 5558:
                tv.setText("3");
                self_num = 0.3;
                break;
            case 5560:
                tv.setText("4");
                self_num = 0.4;
                break;
            case 5562:
                tv.setText("5");
                self_num = 0.5;
                break;
            default:
                Log.i("Hello error", "This is not possible");
        }

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket\n" + e);
            return;
        }
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        Button send = (Button) findViewById(R.id.button4);
        final EditText msgEditText = (EditText) findViewById(R.id.editText1);

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = msgEditText.getText().toString();
                msgEditText.setText("");
                seq_num+=1;
                //msg = String.valueOf((int)(self_num * 10)) + " " + String.valueOf(seq_num) + " " + msg;
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }


    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            try {
                while(true){
                    Socket s = serverSocket.accept();
                    ObjectInputStream in = new ObjectInputStream(s.getInputStream());
                    String msg = (String) in.readObject();

                    if(!msg.equals(null) && msg.substring(0,12).equals("Failed Port:")){
                        String fp = msg.substring(13);

                        if(Integer.parseInt(fp) != -1){
                            failed_port = Integer.parseInt(fp);
                        }
                        Log.i("Oyeh", ""+failed_port);
                        Log.i("Oyeh fp",fp);


                        while(msgs_array.size() > 0){
                            Data datum = msgs_array.get(0);
                            if(datum.port == failed_port){
                                msgs_array.remove(datum);
                            }
                            else if(!datum.to_deliver){
                                break;
                            }
                            else{
                                Log.i("Hello message",datum.msg);
                                publishProgress(datum.msg);
                                msgs_array.remove(0);
                            }
                        }
                    }

                    if(!msg.equals(null) && msg.substring(0,4).equals("AN: ")){
                        msg = msg.substring(4);
                        Log.i("Hello","!!!!!!! ACCEPTED NUMBER RECIEVED !!!!!!!!");
                        Log.i("Hello accpeted number r", msg);
                        String [] m_an = msg.split(":");
                        String msg_ = m_an[0];
                        double an_ = Double.parseDouble(m_an[1]);
                        if(Integer.parseInt(m_an[2]) != -1){
                            failed_port = Integer.parseInt(m_an[2]);
                        }
                        Log.i("Oye failed port an", String.valueOf(failed_port));
                        num = Math.max((int) Math.floor(an_),num);
                        Data data = hashMap.get(msg_);
                        data.updateAN(an_);
                        Collections.sort(msgs_array,new DataComparator());
                        for(int m_i = 0; m_i < msgs_array.size(); m_i++){
                           Log.i("Hello array list an", String.valueOf(msgs_array.get(m_i)));
                        }

                        while(msgs_array.size() > 0){
                            Data datum = msgs_array.get(0);
                            if(datum.port == failed_port){
                                msgs_array.remove(datum);
                            }
                            else if(!datum.to_deliver){
                                break;
                            }
                            else{
                                Log.i("Hello message",datum.msg);
                                publishProgress(datum.msg);
                                msgs_array.remove(0);
                            }
                        }

                    }
                    else {
                        int port = -1;
                        if(!msg.equals(null) && !msg.substring(0,12).equals("Failed Port:")) {
                            String[] msg_port = msg.split(" ");
                            msg = msg_port[0].trim();
                            port = Integer.parseInt(msg_port[1].trim());
                            if(Integer.parseInt(msg_port[2].trim()) != -1) {
                                failed_port = Integer.parseInt(msg_port[2].trim());
                            }
                            Log.i("Oye reciever" + ctr, msg);
                            Log.i("Oye port number", String.valueOf(port));
                            Log.i("Oye failed port num",String.valueOf(failed_port));
                            num += 1;
                            double proposal_num = (double) num + self_num;
                            Data data = new Data(msg, proposal_num, port);
                            hashMap.put(msg, data);
                            msgs_array.add(data);
                            Collections.sort(msgs_array, new DataComparator());
                            for (int m_i = 0; m_i < msgs_array.size(); m_i++) {
                                Log.i("Hello array list r", String.valueOf(msgs_array.get(m_i)));

                            }


                            Log.i("Hello proposal num", String.valueOf(proposal_num));
                            ctr += 1;
                            try {
                                ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                                out.writeObject(String.valueOf(proposal_num));
                            }
                            catch (Exception e){
                                Log.i("Bye","Handle exception here");
                            }

                        }
                    }


                    //ObjectInputStream in1 = new ObjectInputStream(s.getInputStream());

                    //String an = (String) in.readObject();
                    //Log.i("Hello an r",an);

                        //publishProgress(msg, in.readLine());

                }
            }catch (IOException e) {
                e.printStackTrace();
            }
            catch (ClassNotFoundException e) {
                e.printStackTrace();
            }finally {
                Log.i("Hello", "server finally");
            }
            return null;
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */

            String strReceived = strings[0];
            Log.v("Hello about to write ",strReceived);


            TextView displayTextView = (TextView) findViewById(R.id.textView1);
            displayTextView.append(file_key+": "+strReceived+"\n");

            ContentValues contentValues = new ContentValues();
            contentValues.put("key",String.valueOf(file_key));
            contentValues.put("value", strReceived);
            file_key++;
//            sequenceNumber++;
            getContentResolver().insert(mUri,contentValues);


            return;
        }
    }


    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {

            String msg = msgs[0];
            int port = (int) (self_num * 10);
            String msgToSend = msg +  " " + port + " " + failed_port;
            Log.v("Hi send msg",msgToSend);
            Socket[] sockets = new Socket[5];
            //Socket[] sockets1 = new Socket[5];
            ObjectOutputStream outs[] = new ObjectOutputStream[5];
            //ObjectOutputStream outs1[] = new ObjectOutputStream[5];
            ObjectInputStream ins[] = new ObjectInputStream[5];
            Data m = new Data();

            try{
                for(int i = 0; i < 5;i++){
                    try {
                        Log.i("Hello sender", "" + i);
                        sockets[i] = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(REMOTE_PORTS[i]));
                        outs[i] = new ObjectOutputStream(sockets[i].getOutputStream());
                        outs[i].writeObject(msgToSend);
                    }catch (Exception e) {continue;}
                }
                m.addMsg(msg);
                m.addPort(port);


                double Proposals[] = new double[5];

                for(int i=0;i<5;i++){
                    try {
                        Log.i("Hey abc",""+i);
                        ins[i] = new ObjectInputStream(sockets[i].getInputStream());
                        Proposals[i] = Double.parseDouble((String) ins[i].readObject());
                        Log.i("Hello proposal " + i, String.valueOf(Proposals[i]));
                    }
                    catch(IOException e){
                        failed_port = i+1;
                        Socket[] sockets_repeat = new Socket[5];
                        String failed_msg = "Failed Port: "+failed_port;
                        for(int j = 0; j < 5;j++){
                            try {
                                Log.i("Hello sender", "" + j);
                                sockets_repeat[j] = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(REMOTE_PORTS[j]));
                                outs[j] = new ObjectOutputStream(sockets_repeat[j].getOutputStream());
                                outs[j].writeObject(failed_msg);
                            }catch (Exception e1) {continue;}
                        }
                        Log.i("Hey failed port",""+failed_port);
                        e.printStackTrace();
//                        for(int x=0;x < msgs_array.size();x++){
//                            Data current_data = msgs_array.get(x);
//                            if(current_data.port == failed_port){
//                                msgs_array.remove(x);
//                                //current_data.to_deliver = true;
//                            }
//                        }
                        continue;
                    }catch (Exception e){
                        e.printStackTrace();
                        Log.v("Hey EXCEPTION","Cl");
                    }
                }


                double accepted_num = findMax(Proposals);
                Log.i("Hello accepted num s", String.valueOf(accepted_num));
                m.addAN(accepted_num);
                //Log.i("Hello map obj msg",m.msg);
                //Log.i("Hello map obj AN", String.valueOf(m.accepted_num));

                for(int i=0;i<5;i++){
                    try {
                        Log.i("Hello abc out",""+i);
                        sockets[i] = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(REMOTE_PORTS[i]));
                        outs[i] = new ObjectOutputStream(sockets[i].getOutputStream());
                        outs[i].writeObject("AN: " + m.msg + ":" + m.accepted_num + ":" + failed_port);
                    } catch (Exception e) {continue;}
                }


//            } catch (UnknownHostException e) {
//                Log.e("Hello", "ClientTask UnknownHostException");
//                e.printStackTrace();
//            } catch (IOException e) {
//                Log.e("Hello", "ClientTask socket IOException");
//                e.printStackTrace();
//            } catch (ClassNotFoundException e) {
//                e.printStackTrace();
            } finally{
                Log.i("Hello","Exit client task");
            }

            return null;
        }

        public double findMax(double[] Proposals){
            double max_p = Proposals[0];
            for(int i = 1;i < Proposals.length; i++){
                if(Proposals[i] > max_p){
                    max_p = Proposals[i];
                }
            }
            return max_p;
        }
    }

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }
}
