package com.shenzhou.socketdemo;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.IOException;


public class MainActivity extends Activity{
    private ServiceConnection sc;
    private PrintService printService;
    //ip
    private String myIp = "192.168.3.194";
    //port
    private int myPort = 9001;
    private Button kaiqi,lianjie,fasong;
    private MySocketServer mySocketServer;
    private EditText myedit;

    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    Toast.makeText(MainActivity.this,"开",Toast.LENGTH_LONG).show();
                    break;
                case 2:
                    Toast.makeText(MainActivity.this,"关",Toast.LENGTH_LONG).show();
                    break;
                case 100:
                    Toast.makeText(MainActivity.this,"连接成功",Toast.LENGTH_LONG).show();
                    break;
                case 110:
                    Toast.makeText(MainActivity.this,"连接超时",Toast.LENGTH_LONG).show();
                    break;
                case 120:
                    Toast.makeText(MainActivity.this,"连接地址错误",Toast.LENGTH_LONG).show();
                    break;
                case 130:
                    Toast.makeText(MainActivity.this,"连接异常",Toast.LENGTH_LONG).show();
                    break;
                case 140:
                    Bundle bundle = msg.getData();
                    Toast.makeText(MainActivity.this,"正在重连: " + bundle.get("msgContent"),Toast.LENGTH_LONG).show();
                    break;
            }
            return false;
        }
    });
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        lianjie=findViewById(R.id.lianjie);
        fasong=findViewById(R.id.fasong);
        kaiqi=findViewById(R.id.kaiqi);
        myedit=findViewById(R.id.myedit);
        kaiqi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WebConfig webConfig = new WebConfig();
                webConfig.setPort(9001);
                webConfig.setMaxParallels(10);
                mySocketServer = new MySocketServer(webConfig,mHandler);
                mySocketServer.startServerAsync();
            }
        });
        lianjie.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("dai","当前IP："+getLocalIpAddress(MainActivity.this));
                printService.initSocket(getLocalIpAddress(MainActivity.this), myPort);

            }
        });
        fasong.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //发送指令
//                printService.sendData();
                printService.sendData(myedit.getText()+"");
            }
        });

        /*通过binder拿到service*/
        sc = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                printService = ((PrintService.SocketBinder) service).getService();
                printService.setPrintStatusListener(new PrintService.PrintStatusListener() {
                    @Override
                    public void onPrinterListener(int status) {
                        //开关
                        Message message = mHandler.obtainMessage();
                        message.what = status;
                        mHandler.sendMessage(message);
                    }

                    @Override
                    public void onMessageListener(int msgId, String msgContent) {
                        Message message = mHandler.obtainMessage();
                        message.what = msgId;
                        Bundle bundle = new Bundle();
                        bundle.putString("msgContent", msgContent);
                        message.setData(bundle);
                        mHandler.sendMessage(message);
                    }
                });
//                Log.i("dai","正在连接....");
//                printService.initSocket(myIp, myPort);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                printService = null;
            }
        };

        Intent intent = new Intent(getApplicationContext(), PrintService.class);
        bindService(intent, sc, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            mySocketServer.stopServerAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String int2ip(int ipInt) {
        StringBuilder sb = new StringBuilder();
        sb.append(ipInt & 0xFF).append(".");
        sb.append((ipInt >> 8) & 0xFF).append(".");
        sb.append((ipInt >> 16) & 0xFF).append(".");
        sb.append((ipInt >> 24) & 0xFF);
        return sb.toString();
    }

    /**
     * 获取当前ip地址
     *
     * @param context
     * @return
     */
    public static String getLocalIpAddress(Context context) {
        try {

            WifiManager wifiManager = (WifiManager) context
                    .getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int i = wifiInfo.getIpAddress();
            return int2ip(i);
        } catch (Exception ex) {
            return " 获取IP出错!!!!请保证是WIFI,或者请重新打开网络!\n" + ex.getMessage();
        }
    }
}
