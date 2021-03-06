package com.onlylemi.dr.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.baidu.mapapi.SDKInitializer;
import com.onlylemi.dr.costant_interface.Constant;
import com.onlylemi.dr.util.AsyncImageLoader;
import com.onlylemi.dr.util.BaiduLocate;
import com.onlylemi.dr.util.CheckThread;
import com.onlylemi.dr.util.DiskCache;
import com.onlylemi.dr.util.DiskLruCache;
import com.onlylemi.dr.util.JSONHttp;
import com.onlylemi.dr.util.JSONParse;
import com.onlylemi.dr.util.NetworkJudge;
import com.onlylemi.indoor.R;
import com.onlylemi.parse.Data;
import com.onlylemi.user.Assist;

import java.io.File;

public class ReadyActivity extends Activity {

    public static final String TAG = "ReadyActivity:";

    //延时 handler
    public static Handler handler;
    //表名列表

    private DiskCache diskCache;

    private SharedPreferences.Editor editor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!NetworkJudge.isWifiEnabled(this)) {
            synchronized (ReadyActivity.this) {
                Toast.makeText(ReadyActivity.this, "no wi-fi", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "no wi-fi");
            }
        }
        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case Constant.READY_GO:
                        Log.e(TAG, "NodesContact number : " + Data.nodesContactTableList.size());
                        Log.e(TAG, "city number : " + Data.getCityTableList().size());
                        Log.e(TAG, "Nodes number : " + Data.nodesTableList.size());
                        Log.e(TAG, "floorplan number : " + Data.floorPlanTableList.size());
                        Log.e(TAG, "views number : " + Data.viewTableList.size());
                        Log.e(TAG, "palce number : " + Data.placeTableList.size());
                        Log.e(TAG, "activity number : " + Data.activityTableList.size());
                        Intent intent = new Intent(ReadyActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                        break;
                    default:
                        break;
                }
            }
        };
        DiskCache.initContext(getApplicationContext());
        diskCache = DiskCache.getInstance();

        //百度地图SDK初始化 必须在 setContentView 之前
        SDKInitializer.initialize(getApplicationContext());

        //百度定位初始化
        BaiduLocate.InitContext(getApplicationContext());
        BaiduLocate.getInstance().setMyLocateListener(new BaiduLocate.MyLocateListener() {
            @Override
            public void LocateCallBack(int flag, String city, String address) {
                Log.e(BaiduLocate.TAG, "定位回调监听成功-----" + "address : " + address);
                Log.i(TAG, "city : " + city);
                BaiduLocate.getInstance().stopLocate();
            }
        });
        BaiduLocate.getInstance().startLocate();

        //是否删除文件缓存
        SharedPreferences preferences = this.getPreferences(Context.MODE_PRIVATE);
        editor = preferences.edit();
        int flag = preferences.getInt("Flag", -1);
        Assist.user = preferences.getString("user", "");
        Log.i(TAG, "用户：" + Assist.user);
        if (flag == -1) {
            editor.putInt("Flag", 1);
            JSONParse();//由于数据比较少，直接解析所有JSON数据 存到本地
            editor.apply();
        } else if (flag % 1 == 0) {
            JSONParse();//由于数据比较少，直接解析所有JSON数据 存到本地
            editor.putInt("Flag", ++flag);
            Log.i("Test", "network::::" + --flag);
            editor.apply();
        } else {
            JSONParseFromFile();//从本地获取
            editor.putInt("Flag", ++flag);
            Log.i("Test", "file::::" + --flag);
            editor.apply();
        }


        //判断是否删除图片缓存
        if (NetworkJudge.isWifiEnabled(getApplicationContext())) {
            try {
                String cachePath;
                if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                        || !Environment.isExternalStorageRemovable()) {
                    if (getApplicationContext().getExternalCacheDir() != null) {
                        cachePath = getApplicationContext().getExternalCacheDir().getPath();
                        Log.i("Test", "SD");
                    } else {
                        throw new Exception("SD卡连接错误");
                    }
                } else {
                    cachePath = getApplicationContext().getCacheDir().getPath();
                }
                File cacheDir = new File(cachePath + File.separator + "bitmap");
                Log.i(AsyncImageLoader.TAG, "cache dir :　" + cacheDir);
                if (cacheDir.exists()) {
                    if ((flag - 1) % 1 == 0) {
                        DiskLruCache.deleteContents(cacheDir);
                        Log.i(AsyncImageLoader.TAG, "cache dir :　" + cacheDir);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(ReadyActivity.this, "当前无wifi连接", Toast.LENGTH_SHORT).show();
        }

        setContentView(R.layout.activity_ready);

        CheckThread.getCheckThread().startCheck();
    }

    /**
     * 所有JSON解析
     */
    private void JSONParse() {
        String url = "http://indoor.onlylemi.com/android/?r=place";
        //所有地方信息初始化
        new JSONHttp(url, new JSONHttp.JSONHttpReturn() {
            @Override
            public void JSONReturn(String s) {
                diskCache.write(diskCache.PlaceTableName, s);//写入缓存
                JSONParse.parsePlace(s);
            }
        }).start();

        url = "http://indoor.onlylemi.com/android/?r=activity";
        //所有活动信息初始化
        new JSONHttp(url, new JSONHttp.JSONHttpReturn() {
            @Override
            public void JSONReturn(String s) {
                diskCache.write(diskCache.ActivityTableName, s);//写入缓存
                JSONParse.parseActivity(s);
            }
        }).start();

        url = "http://indoor.onlylemi.com/android/?r=views";
        //所有view信息初始化
        new JSONHttp(url, new JSONHttp.JSONHttpReturn() {
            @Override
            public void JSONReturn(String s) {
                diskCache.write(diskCache.ViewsTableName, s);//写入缓存
                JSONParse.parseView(s);
            }
        }).start();

        url = "http://indoor.onlylemi.com/android/?r=floor_plan";
        //所有floorplan信息初始化
        new JSONHttp(url, new JSONHttp.JSONHttpReturn() {
            @Override
            public void JSONReturn(String s) {
                diskCache.write(diskCache.FloorPlanTableName, s);//写入缓存
                JSONParse.parseFloor(s);
            }
        }).start();

        url = "http://indoor.onlylemi.com/android/?r=nodes_contact";
        //所有nodescontact信息初始化
        new JSONHttp(url, new JSONHttp.JSONHttpReturn() {
            @Override
            public void JSONReturn(String s) {
                diskCache.write(diskCache.NodesContactTableName, s);//写入缓存
                JSONParse.parseNodesContact(s);
            }
        }).start();

        url = "http://indoor.onlylemi.com/android/?r=nodes";
        //所有nodes信息初始化
        new JSONHttp(url, new JSONHttp.JSONHttpReturn() {
            @Override
            public void JSONReturn(String s) {
                diskCache.write(diskCache.NodesTableName, s);//写入缓存
                JSONParse.parseNodes(s);
            }
        }).start();

        //城市列表 初始化
        url = "http://indoor.onlylemi.com/android/?r=city";
        JSONHttp http = new JSONHttp(url, new JSONHttp.JSONHttpReturn() {
            @Override
            public void JSONReturn(String s) {
                diskCache.write(diskCache.CityTableName, s);//写入缓存
                JSONParse.parseCity(s);
                handler.sendEmptyMessageDelayed(Constant.READY_GO, 1000);
            }
        });
        http.setPriority(Thread.MIN_PRIORITY);
        http.start();
    }

    private void JSONParseFromFile() {
        new Thread(new Runnable() {
            @Override
            public void run() {

                //地方信息初始化
                String s = diskCache.read(diskCache.PlaceTableName);
                JSONParse.parsePlace(s);

                //活动信息初始化
                s = diskCache.read(diskCache.ActivityTableName);
                JSONParse.parseActivity(s);

                //所有view信息初始化
                s = diskCache.read(diskCache.ViewsTableName);
                JSONParse.parseView(s);

                //所有floorplan信息初始化
                s = diskCache.read(diskCache.FloorPlanTableName);
                JSONParse.parseFloor(s);

                //所有nodescontact信息初始化
                s = diskCache.read(diskCache.NodesContactTableName);
                JSONParse.parseNodesContact(s);
                //所有nodes信息初始化
                s = diskCache.read(diskCache.NodesTableName);
                JSONParse.parseNodes(s);
                //城市信息初始化
                s = diskCache.read(diskCache.CityTableName);
                JSONParse.parseCity(s);
            }
        }).start();
    }
}
