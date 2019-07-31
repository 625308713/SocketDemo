package com.shenzhou.socketdemo;

import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MySocketServer {
    private boolean isEnable;
    private final WebConfig webConfig;//配置信息类
    private final ExecutorService threadPool;//线程池
    private ServerSocket socket;
    private Handler mHandler;

    public MySocketServer(WebConfig webConfig,Handler mHandler) {
        this.webConfig = webConfig;
        threadPool = Executors.newCachedThreadPool();
        this.mHandler = mHandler;
    }

    /**
     * 开启server
     */
    public void startServerAsync() {
        isEnable=true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                doProcSync();
            }
        }).start();
    }

    /**
     * 关闭server
     */
    public void stopServerAsync() throws IOException {
        if (!isEnable){
            return;
        }
        isEnable=false;
        socket.close();
        socket=null;
    }

    private void doProcSync() {
        try {
            InetSocketAddress socketAddress=new InetSocketAddress(webConfig.getPort());
            socket=new ServerSocket();
            socket.bind(socketAddress);
            while (isEnable){
                final Socket remotePeer= socket.accept();
                threadPool.submit(new Runnable() {
                    @Override
                    public void run() {
                        onAcceptRemotePeer(remotePeer);
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void onAcceptRemotePeer(Socket remotePeer) {
        try {
            Log.i("dai","connected successful连接成功!");
//            remotePeer.getOutputStream().write("connected successful".getBytes());//告诉客户端连接成功
            // 从Socket当中得到InputStream对象
            InputStream inputStream = remotePeer.getInputStream();
            byte buffer[] = new byte[1024 * 4];
            int temp = 0;
            // 从InputStream当中读取客户端所发送的数据
            while ((temp = inputStream.read(buffer)) != -1) {
                Log.i("dai","客户端发送内容:"+new String(buffer, 0, temp, "UTF-8"));
//                remotePeer.getOutputStream().write(buffer, 0, temp);//把客户端传来的消息发送回去
                if("1".equals(new String(buffer, 0, temp, "UTF-8"))){
                    mHandler.sendEmptyMessage(1);
                }else{
                    mHandler.sendEmptyMessage(2);
                }
            }
            remotePeer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
