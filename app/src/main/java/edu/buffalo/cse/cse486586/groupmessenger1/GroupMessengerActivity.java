package edu.buffalo.cse.cse486586.groupmessenger1;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 */
public class GroupMessengerActivity extends Activity {
    EditText editText;
    TextView tv;
    private static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static int sequenceNumber = -1; // Started the Sequence Number from -1 so that on increment the first file would be 0.
    static final String KEY_FIELD = "key"; // Got the context of how to build mUri,KEY and VALUE from PTestClickListener.
    static final String VALUE_FIELD = "value";
    public Uri mUri;
    private String[] redirectPorts = {"11108", "11112", "11116", "11120", "11124"}; //5 Ports for 5 AVDs
    static final int SERVER_PORT = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        /*
            The below three line of code is same as that in Simple Messenger. Get PORT number.
         */

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        /*
            The following code will display the text once ENTER Button is clicked at EditText. The
            text is displayed only in the Local AVD as I found it to be too cluttering to display
            it to rest of the AVDs.
         */
        tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        editText = (EditText) findViewById(R.id.editText1);
        editText.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                        (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    String msg = editText.getText().toString() + "\n";
                    editText.setText(""); // This is one way to reset the input box.
                    tv.append("\t" + msg); // This is one way to display a string.
                    return true;
                }
                return false;
            }
        });

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
        /*
            mUri build similar to how it is shown in OnPTestClickListener
         */
        mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger1.provider");
        /*
            Server Socket is created as THREAS_POOL_EXECUTOR
         */
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }
        /*
            onClickListener set for Button4 that is "Send" button to initiate ClientTask.
         */
        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText.getText().toString();
                editText.setText("");
                tv.append(msg + "\t\n");
                tv.append("\n");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    /*
       Used same code to build URI in OnPtestClickListener class
     */

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    /*
        The logic used to create ServerTask class is similar to what I implemented in Simple Messenger.
        The idea here is to send an ack from Server to Client after Server receives a message.
     */
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            Socket socket = null;
            do {
                try {
                    socket = serverSocket.accept();
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(socket.getOutputStream());
                    String msg = in.readLine();
                    Log.i(TAG, "The client sent " + msg); // get msg from client
                    out.write("Got the msg at server " + msg);// tell client that you got the msg
                    out.flush();
                    out.close();
                    publishProgress(msg); //send msg to onProgressUpdate.
                    in.close();
                    socket.close();
                    Log.i(TAG, "Server Socket Closed");
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Handle IOException at server");
                }
            } while (!socket.isInputShutdown());
            return null;
        }

        protected void onProgressUpdate(String... strings) {
            String strReceived;
            if (strings[0] == null) {
                strReceived = "null";
            } else {
                strReceived = strings[0].trim();
            }
            TextView localTextView = (TextView) findViewById(R.id.textView1);
            localTextView.append("\n");
            /*
                Insert KEY and VALUE fields via insert method implemented in Content provider.
             */
            ContentValues values = new ContentValues();
            values.put(KEY_FIELD, String.valueOf(++sequenceNumber));
            values.put(VALUE_FIELD, strReceived);
            new GroupMessengerProvider().insert(mUri, values);
        }
    }

    /*
        The Client task is similar to what I implemented in SimpleMessenger. The idea is to wait for
        the ack from Server before closing the Socket.
        I had used PrintWriter to get OutputStream in Simple Messenger but in this case I used
        PrintStream this is because using PrintWriter only msg to AVD0.
        I used the below URL to understand this.:
        https://stackoverflow.com/questions/47979173/sending-message-from-server-to-multiple-clients-in-java

     */

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {
                for (String remotePort : redirectPorts) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort));

                    String msgToSend = msgs[0];
                    if (socket.isConnected()) {
                        Log.i(TAG, remotePort + " is connected");
                        //PrintWriter out = new PrintWriter(socket.getOutputStream());
                        PrintStream ps = new PrintStream(socket.getOutputStream());
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        ps.println(msgToSend);
                        ps.flush();
                        Log.i(TAG, "Server sent " + in.readLine());
                        in.close();
                        ps.close();
                    }
                    socket.close();
                    Log.i(TAG, "Client Socket Closed for " + remotePort);
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            }

            return null;
        }
    }

}
