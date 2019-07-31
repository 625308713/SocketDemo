package com.shenzhou.socketdemo;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * socket连接服务
 */
public class PrintService extends Service {

    private String mPrinterIP = "192.168.32.14";
    private int mPrinterPort = 9100;
    private int mConnectTimeOut = 5000;


    private InputStream mInStream = null;
    private OutputStream mOutStream = null;
    private BufferedWriter mBufferedWriter = null;
    private Socket mSocket = null;
    /*默认重连*/
    private boolean isReConnect = true;
    private ConnectThread mConnectThread = null;
    /*是否退出接收线程*/
    private volatile boolean isCancel = false;

    /*重连次数*/
    private int mConnectCount = 3;
    private int mReConnCount = 0;

    private SocketBinder sockerBinder = new SocketBinder();

    private PrintStatusListener printStatusListener;

    public PrintService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return sockerBinder;
    }

    public class SocketBinder extends Binder {
        public PrintService getService() {
            return PrintService.this;
        }
    }

    /*初始化socket*/
    public void initSocket(String ip, int port) {
        if(mSocket == null){
            isCancel = false;
            mConnectThread = new ConnectThread(ip, port);
            mConnectThread.start();
        }
    }

    public void setPrintStatusListener(PrintStatusListener printStatusListener) {
        this.printStatusListener = printStatusListener;
    }

    /**
     * 连接线程
     */
    private class ConnectThread extends Thread {

        public ConnectThread(String ip, int port){
            mPrinterIP = ip;
            mPrinterPort = port;
        }

        @Override
        public void run() {
            super.run();
            try {
                mSocket = new Socket();
                mSocket.connect(new InetSocketAddress(mPrinterIP, mPrinterPort), mConnectTimeOut);
                if (mSocket.isConnected()) {
                    mOutStream= mSocket.getOutputStream();
                    mBufferedWriter = new BufferedWriter(new OutputStreamWriter(mOutStream, "GBK"));
                    mInStream = mSocket.getInputStream();

                    printStatusListener.onMessageListener(100, "");
                    mReConnCount = 0;

                    //开启读取线程
                    new ReadThread().start();
                }
            }catch (Exception e){
              e.printStackTrace();
                if (e instanceof SocketTimeoutException) {
                    printStatusListener.onMessageListener(110, "");
                    releaseAndReConnect();
                } else if (e instanceof NoRouteToHostException) {
                    printStatusListener.onMessageListener(120, "");
                    releaseAndReConnect();
                } else if (e instanceof ConnectException) {
                    printStatusListener.onMessageListener(130, "");
                    releaseAndReConnect();
                }
            }
        }
    }

    /**
     * 接收指令线程
     */
    private class ReadThread extends Thread {
        @Override
        public void run() {
            super.run();
            while(!isCancel){
                if(!isConnected()){
                    break;
                }
                try {
                    if(mInStream != null){
                        int size = 0;
                        byte[] initBuffer= new byte[4];
                        //网络通讯往往是间断性的，一串字节往往分几批进行发送。本地程序调用available()方法有时得到0，
                        // 这可能是对方还没有响应，也可能是对方已经响应了，但是数据还没有送达本地
                        //接到数据再往下走
                        int count = 0;
                        while (count == 0) {
                            count = mInStream.available();
                        }
                        size = mInStream.read(initBuffer);
                        StringBuilder stringBuilder = new StringBuilder();
                        for(int i=0;i<size;i++){
                            stringBuilder.append(Byte2String(initBuffer[i]));
                        }
                        String result = stringBuilder.toString();
                        if("开".equals(result)){
                            printStatusListener.onPrinterListener(1);
                        }else if("关".equals(result)){
                            printStatusListener.onPrinterListener(2);
                        }

                    }
                    Thread.sleep(500);
                }catch (Exception e){
                    e.printStackTrace();
                    releaseAndReConnect();
                }
            }
        }
    }

    public String Byte2String(byte b){
        String hex = Integer.toHexString(b & 0xFF);
        if (hex.length() == 1) {
            hex = '0' + hex;
        }
        return hex;
    }



    /**
     * 发送指令线程
     */
    private class WriteThread extends Thread {

        private String data;

        public WriteThread(String data){
            this.data = data;
        }

        @Override
        public void run() {
            super.run();
            if (!isConnected()) {
                releaseAndReConnect();
            }
            try {
                if(mBufferedWriter != null){
                    mBufferedWriter.write(data);
                    mBufferedWriter.flush();

                }
            } catch (Exception e) {
                e.printStackTrace();
                releaseAndReConnect();
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 有无数据都发送指令线程,防止时间过久不和打印机通信导致断开连接
     */
    private class WriteThread2 extends Thread {
        @Override
        public void run() {
            super.run();
            if (!isConnected()) {
                releaseAndReConnect();
            }
            try {
                if(mBufferedWriter != null){
                    byte buffer = 0;
                    mBufferedWriter.write(buffer);
                    mBufferedWriter.flush();
                }
            } catch (Exception e) {
                e.printStackTrace();
                releaseAndReConnect();
            }
        }
    }




    /**
     * 发送数据、指令
     * @param data
     */
    public void sendData(String data){
        new WriteThread(data).start();
    }

    //发送空指令避免中断连接
    public void sendData(){
        new WriteThread2().start();
    }

    /**
     * 判断本地socket连接状态
     */
    private boolean isConnected() {
        if (mSocket != null && (mSocket.isClosed() || !mSocket.isConnected() ||
                mSocket.isInputShutdown() || mSocket.isOutputShutdown())) {
            return false;
        }
        return true;
    }

    /*释放资源*/
    public void releaseSocket() {
        isCancel = true;
        if(mBufferedWriter != null){
            try {
                mBufferedWriter.close();
                mBufferedWriter = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if(mOutStream != null){
            try {
                mOutStream.close();
                mOutStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(mInStream != null){
            try {
                mInStream.close();
                mInStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(mSocket != null){
            try {
                mSocket.close();
                mSocket = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mConnectThread != null) {
            mConnectThread = null;
        }
    }

    public void releaseAndReConnect(){
        releaseSocket();

        if(isReConnect){
            if(mConnectCount > 0) {
                initSocket(mPrinterIP, mPrinterPort);
                mReConnCount++;
                printStatusListener.onMessageListener(140, "第"+ mReConnCount +"次重连");
                mConnectCount--;
            }else{
                mReConnCount=0;
            }
        }
    }

    @Override
    public void onDestroy() {
        isReConnect = false;
        releaseSocket();
        super.onDestroy();
    }

    public interface PrintStatusListener {
        //服务返回状态
        void onPrinterListener(int status);
        //错误状态
        void onMessageListener(int msgId, String msgContent);

    }
}
