package myandroid.com.videotransfer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;


public class MainActivity extends Activity implements View.OnClickListener {

    private static final int CAPUTREREQUESTCODE = 1;
    private static final int OPENREQUESTCODE = 2;
    public static final String MAINACTIVITY = "MainActivity";

    VideoView videoView;
    Button btnOpen, btnRecord, btnSend, btnExit;
    String videoPath = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnOpen = (Button) findViewById(R.id.btnOpen);
        btnRecord = (Button) findViewById(R.id.btnRecord);
        btnSend = (Button) findViewById(R.id.btnSend);
        btnExit = (Button) findViewById(R.id.btnExit);
        videoView = (VideoView) findViewById(R.id.videoView);

        btnOpen.setOnClickListener(this);
        btnRecord.setOnClickListener(this);
        btnSend.setOnClickListener(this);
        btnExit.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btnOpen:
                Intent intentOpen = new Intent(Intent.ACTION_GET_CONTENT);
                intentOpen.setType("video/*");
                startActivityForResult(intentOpen, OPENREQUESTCODE);
                break;
            case R.id.btnRecord:
                Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                startActivityForResult(intent, CAPUTREREQUESTCODE);
                break;
            case R.id.btnSend:
                if (videoPath == null) {
                    Toast.makeText(MainActivity.this, R.string.no_select_video, Toast.LENGTH_SHORT).show();
                } else {
                    MyAsyncTask myAsyncTask = new MyAsyncTask();
                    myAsyncTask.execute("http://172.20.32.3:8088/videotransfer/DO");
//                    myAsyncTask.execute("http://192.168.0.94:8088/videotransfer/DO");
                }
                break;
            case R.id.btnExit:
                finish();
                break;
            case R.id.videoView:
                if (videoView.isPlaying()) {
                    Log.e(MAINACTIVITY, "video view pause");
                    videoView.pause();
                } else if (!videoView.isPlaying()) {
                    videoView.resume();
                    Log.e(MAINACTIVITY, "video view resume");
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null) {
            Log.e(MAINACTIVITY, "开始判断null值");
            if (videoPath == null) {
                Toast.makeText(MainActivity.this, R.string.no_select_video, Toast.LENGTH_SHORT).show();
            }
            return;
        } else {
            Uri uri = data.getData();
            if (requestCode == CAPUTREREQUESTCODE) {
                if (resultCode == Activity.RESULT_OK) {
                    Log.e(MAINACTIVITY, "摄像uri.getPath：" + uri.getPath());
                    Log.e(MAINACTIVITY, "摄像uri.scheme:" + uri.getScheme());
                    videoPath = getVideoPath(uri);
                    Log.e(MAINACTIVITY, "摄像videoPath:" + videoPath);

                    setVideo2View(videoPath);
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    Toast.makeText(MainActivity.this, R.string.no_capture_video, Toast.LENGTH_SHORT).show();
                }
            } else if (requestCode == OPENREQUESTCODE) {
                if (resultCode == Activity.RESULT_OK) {
                    Log.e(MAINACTIVITY, "打开uri path:" + uri.getPath());
                    Log.e(MAINACTIVITY, "打开uri scheme:" + uri.getScheme());
                    videoPath = uri.getPath();
                    if (uri.getScheme().startsWith("content")) {
                        videoPath = getVideoPath(uri);
                    }
                    Log.e(MAINACTIVITY, "打开videoPath:" + videoPath);

                    setVideo2View(videoPath);
                }
            }
        }
    }

    protected String getVideoPath(Uri uri) {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor.moveToFirst()) {
            videoPath = cursor.getString(cursor.getColumnIndex("_data"));
            Log.e(MAINACTIVITY, videoPath);
        }
        return videoPath;
    }

    protected void setVideo2View(String videoPath) {
        videoView.setVideoPath(videoPath);
        videoView.setMediaController(new MyMediaController(this));
        videoView.start();
    }

    public class MyAsyncTask extends AsyncTask<String, Integer, String> {

        String message = null;
        ProgressDialog mProgressDialog = null;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            mProgressDialog = new ProgressDialog(MainActivity.this);
            mProgressDialog.setCancelable(true);
            mProgressDialog.setMessage("文件上传中...");
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.setProgress(0);
            mProgressDialog.setMax(100);
            mProgressDialog.show();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            if (values[0] < 100) {
                mProgressDialog.setProgress(values[0]);
            }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            mProgressDialog.dismiss();
            if (s.startsWith("上传成功")) {
                videoPath = null;
            }
            new AlertDialog.Builder(MainActivity.this)
                    .setMessage(s)
                    .setTitle(R.string.resulttip)
                    .setPositiveButton(R.string.alert_dialog_btn_text, null)
                    .setCancelable(false)
                    .show();
        }

        @Override
        protected String doInBackground(String... params) {

            try {
                File file = new File(videoPath);
                if (file.exists()) {
                    Log.e(MAINACTIVITY, "filename:" + file.getName());
                    Log.e(MAINACTIVITY, "file can read:" + file.canRead());
                    Log.e(MAINACTIVITY, "file exists:" + file.exists());
                    Log.e(MAINACTIVITY, "file path:" + file.getPath());
                    FileInputStream fis = new FileInputStream(file);

                    URL url = new URL(params[0]);
                    Log.e(MAINACTIVITY, "url protocol:" + url.getProtocol());
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    Log.e(MAINACTIVITY, "connection connected");
                    connection.setDoOutput(true);
                    connection.setDoInput(true);
                    connection.setConnectTimeout(3000);
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-type", "application/x-java-serialized-object");

                    OutputStream out = connection.getOutputStream();
                    BufferedOutputStream bufo = new BufferedOutputStream(out);

                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(bufo);
                    objectOutputStream.writeObject(file);
                    objectOutputStream.flush();

                    int total = fis.available();
                    Log.e(MAINACTIVITY, total + "length of file");
                    byte[] buf = new byte[1024];
                    int count = 0;
                    int len;
                    while ((len = fis.read(buf)) != -1) {
                        bufo.write(buf, 0, len);
                        count += len;
                        publishProgress((int) ((count / (float) total) * 100));
                    }
                    bufo.flush();
                    fis.close();

                    InputStream in = connection.getInputStream();
                    BufferedReader bufr = new BufferedReader(new InputStreamReader(in));
                    while ((message = bufr.readLine()) != null) {
                        if (message.startsWith("OK")) {
                            return "上传成功！";
                        } else if (message.endsWith("NO")) {
                            return "上传失败，请重试！";
                        }
                    }
                }
            } catch (MalformedURLException e) {
                Log.e(MAINACTIVITY, "MDURL" + e.toString());
                e.printStackTrace();
                return "出错了，请重试！";
            } catch (FileNotFoundException e) {
                Log.e(MAINACTIVITY, "FNF" + e.toString());
                e.printStackTrace();
                return "出错了，请重试！";
            } catch (ProtocolException e) {
                Log.e(MAINACTIVITY, "protocol" + e.toString());
                e.printStackTrace();
                return "出错了，请重试！";
            } catch (SocketTimeoutException e) {
                Log.e(MAINACTIVITY, "socket time out" + e.toString());
                e.printStackTrace();
                return "网络连接超时";
            } catch (IOException e) {
                Log.e(MAINACTIVITY, "IO" + e.toString());
                e.printStackTrace();
                return "出错了，请重试！";
            }
            Log.e(MAINACTIVITY, "return null");
            return "未知错误，请重试！";
        }

    }
}
