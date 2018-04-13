package com.example.administrator.testone;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

import butterknife.ButterKnife;
import butterknife.OnClick;

//import butterknife.BindView;

public class ReceiveActivity extends AppCompatActivity {
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private static final int PORT = 1234;
//    @BindView(R.id.mIP)
    TextView mIP;
//    @BindView(R.id.yIP)
    TextView yIP;
//    @BindView(R.id.video_screen)
    ImageView Image;
//    @BindView(R.id.button2)
    Button btn;
    private Handler mHandler = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive);
        ButterKnife.bind(this);

        Button button2 = (Button)findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Intent intent = new Intent(ReceiveActivity.this, MainActivity.class);
                //startActivity(intent);
                System.exit(0);
            }
        });

        checkPermission();
        //mIP.setText(getIp() + "     本地端口:" + PORT);
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectLeakedSqlLiteObjects().detectLeakedClosableObjects().penaltyLog().penaltyDeath().build());
        mHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch (msg.what) {
                    case 1:
                        yIP.setText((String) msg.obj);
                        break;
                    case 2:
                        Image.setImageBitmap((Bitmap) msg.obj);
                        break;
                }
                return true;
            }
        });
        new SeverReader().start();
    }

    private String getIp() {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        //检查Wifi状态
        if (!wm.isWifiEnabled())
            wm.setWifiEnabled(true);
        WifiInfo wi = wm.getConnectionInfo();
        //获取32位整型IP地址
        int ipAdd = wi.getIpAddress();
        //把整型地址转换成“*.*.*.*”地址
        String ip = intToIp(ipAdd);
        return ip;
    }

    private String intToIp(int i) {
        return (i & 0xFF) + "." +
                ((i >> 8) & 0xFF) + "." +
                ((i >> 16) & 0xFF) + "." +
                (i >> 24 & 0xFF);
    }

    class SeverReader extends Thread {
        @Override
        public void run() {
            super.run();
            //receiveFile();
            try {
                receiveFile1();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private static int files = 1;
    private long startTime = 0;

    private ServerSocket mServerSocket = null;
    private Socket mSocket = null;
    private InputStream mInputStream = null;

    public synchronized void receiveFile1() throws IOException {
        mServerSocket = new ServerSocket(PORT);
        while (true) {
            try {
                //listen on 1234
                mSocket = mServerSocket.accept();
                String ip = mSocket.getInetAddress().getHostAddress() + "     本地端口:" + mSocket.getPort();
                Message message1 = mHandler.obtainMessage();
                message1.obj = ip;
                message1.what = 1;
                mHandler.sendMessage(message1);
                mInputStream = mSocket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            while (true) {
                byte[] sizeArray = new byte[4];
                try {
                    mInputStream.read(sizeArray);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                int picLength = ByteBuffer.wrap(sizeArray).asIntBuffer().get();
                System.out.println("picLength:" + picLength);
                if (picLength != 0) {
                    int totalLen = 0;
                    int bags = 0;
                    int len;
                    byte[] buf = new byte[1460];
                    File file = new File(Environment.getExternalStorageDirectory().toString() + "/图片/",
                            String.format("第%d张.jpg", files));
                    OutputStream saveFile = new FileOutputStream(file);
                    startTime = System.nanoTime();
                    while ((len = mInputStream.read(buf)) != -1) {
                        if (len != 0) {
                            saveFile.write(buf, 0, len);
                            totalLen += len;
                            bags++;
                            System.out.print("服务器接收第" + bags + "个包,");
                            System.out.println("大小为" + len + "共" + totalLen);
                            if (totalLen == picLength) break;
                        }
                    }
                    long endTime = System.nanoTime();
                    int gap = (int) ((endTime - startTime) / 1000000);
                    double speed = 1000d /1024d * picLength / gap;
                    saveFile.flush();
                    saveFile.close();
                    files++;
                    System.out.println(String.format("第%d张图片接收完毕！共%d字节，花了%d毫秒,平均速度为%.2fKB/S",
                            files, picLength, gap, speed));
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(file.toString(), options);
                    options.inPreferredConfig = Bitmap.Config.ARGB_4444;
                    options.inSampleSize = calculateInSampleSize(options, Image.getWidth(), Image.getHeight());
                    options.inJustDecodeBounds = false;
                    Bitmap bitmap = BitmapFactory.decodeFile(file.toString(), options);
                    Message message2 = mHandler.obtainMessage();
                    message2.obj = bitmap;
                    message2.what = 2;
                    mHandler.sendMessage(message2);
                } else break;
            }
        }
    }

    public int calculateInSampleSize(BitmapFactory.Options op, int reqWidth,
                                     int reqheight) {
        int originalWidth = op.outWidth;
        int originalHeight = op.outHeight;
        int inSampleSize = 1;
        if (originalWidth > reqWidth || originalHeight > reqheight) {
            int halfWidth = originalWidth / 2;
            int halfHeight = originalHeight / 2;
            while ((halfWidth / inSampleSize > reqWidth)
                    && (halfHeight / inSampleSize > reqheight)) {
                inSampleSize *= 2;

            }
        }
        return inSampleSize;
    }

    private void checkPermission() {
        //检查权限（NEED_PERMISSION）是否被授权 PackageManager.PERMISSION_GRANTED表示同意授权
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            //用户已经拒绝过一次，再次弹出权限申请对话框需要给用户一个解释
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission
                    .WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(this, "请开通相关权限，否则无法正常使用本应用！", Toast.LENGTH_SHORT).show();
            }
            //申请权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL_STORAGE);

        } else {
            Toast.makeText(this, "授权成功！", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mInputStream != null) {
            try {
                mInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (mSocket != null) {
            if (!mSocket.isClosed()) {
                try {
                    mSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (mServerSocket != null) {
            if (!mServerSocket.isClosed()) {
                try {
                    mServerSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

