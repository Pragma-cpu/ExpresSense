package com.example.smiledetection;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.google.android.youtube.player.YouTubeBaseActivity;
import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayerView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RequiresApi(api = Build.VERSION_CODES.R)
public class MainActivity extends YouTubeBaseActivity {
    private static final String API_KEY="add your API key here";
    // CONSTANTS
    String videoUrl ;
    double CHIRP_DURATION = 0.040;
    double SILENCE_DURATION = 0.040;
    int SAMPLE_RATE = 44100;
    int NUMBER_OF_SAMPLE = (int) ((2*SILENCE_DURATION + 2*CHIRP_DURATION) * SAMPLE_RATE);
    double[] sample = new double[NUMBER_OF_SAMPLE];
    byte[] GENERATED_SOUND = new byte[2 * NUMBER_OF_SAMPLE];
    private int BUFFER_SIZE = 20000;

    //Phaser representation
    int NUMBER_OF_POINTS = 50;
    int POINTS_FOR_INITIAL = 5;
    double[][] points = new double[NUMBER_OF_POINTS][2];
    int iterator = 0;
    boolean FreqBinInitialized = false;
    boolean ThresholdInitialized = false;
    //private Boolean direct = false;

    // PLAYER
    AudioTrack audioTrack,audioTrackD;
    AudioManager audioManager, audioManagerD;
    Thread recordingThread = null;
    Thread processingThread = null;
    private VideoView videoView;

    //Display
    TextView textView;
    TextView freqBin;
    EditText expression;
    ImageView sleepTracker, smileTracker;
    MediaPlayer md;
    File pcmFile;
    TextView predExp;
    //VideoView videoView;
    YouTubePlayerView mYouTubePlayerView;
    YouTubePlayer.OnInitializedListener mOnInitializedListener;
    Button bhappy, bsad, bangry, bsur, bmix;
    RadioGroup radioGroup;
    int lenVid, typeVid;
    int passIntent[];
    long startTime = System.currentTimeMillis();
    int[][] timeExp;
    int tEctr=0;



    // CIRCULAR BUFFER
    private CircularBuffer circularBuffer = new CircularBuffer(BUFFER_SIZE);

    //SIGNAL PROCESSING
    private SignalProcessor signalProcessor = new SignalProcessor(this);

    private static final String TAG = "AudioRecordTest";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int WRITE_EXTERNAL_STORAGE_PERMISSION = 200;


    // PERMISSIONS
    private final String [] permissions = {Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.MODIFY_AUDIO_SETTINGS};


    Thread thread;

    void genTone(){

        double F11 = 16000, F12 = 19000;
        double Ftone = 19000;
        int j = 0;

        for(int i=0; i < CHIRP_DURATION*SAMPLE_RATE; i++)
        {
            double c = 1000 / CHIRP_DURATION;
            sample[j++] = Math.sin(2 * Math.PI * (c / 2 * i / SAMPLE_RATE + F11) * i / SAMPLE_RATE);
        }

        for(int i=0; i < SILENCE_DURATION*SAMPLE_RATE; i++)
        {
            sample[j++] = 0;
        }

        for(int i=0; i < CHIRP_DURATION*SAMPLE_RATE; i++)
        {
            double c = 1000 / CHIRP_DURATION;
            sample[j++] = Math.sin(2 * Math.PI * (c / 2 * i / SAMPLE_RATE + F11) * i / SAMPLE_RATE);
        }

        for(int i=0; i < SILENCE_DURATION*SAMPLE_RATE; i++)
        {
            sample[j++] = 0;
        }

        int idx = 0;
        for (final double dVal : sample) {
            // scale to maximum amplitude
            final short val = (short) (dVal * 32767); // max positive sample for signed 16 bit integers is 32767
            // in 16 bit wave PCM, first byte is the low order byte (pcm: pulse control modulation)
            GENERATED_SOUND[idx++] = (byte) (val & 0x00ff);
            GENERATED_SOUND[idx++] = (byte) ((val & 0xff00) >>> 8);
        }
    }

    void playSound() {
        audioTrack.write(GENERATED_SOUND, 0, GENERATED_SOUND.length);
        audioTrack.setLoopPoints(0, GENERATED_SOUND.length/2, -1);
        audioTrack.play();
    }


    private static final int RECORDER_SAMPLE_RATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private AudioRecord recorder = null;
    private Boolean isRecording = false;
    private Boolean direct = false;

    int bufferElements2Rec = 2048; // want to play 2048 (2K) since 2 bytes we use only 1024
    int bytesPerElement = 2; // 2 bytes in 16bit format

    private void startRecording() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLE_RATE,
                RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING,
                bufferElements2Rec * bytesPerElement);

        recorder.startRecording();
        isRecording = true;

        recordingThread = new Thread(new Runnable() {
            public void run() {
                writeAudioDataToBuffer();
            }
        }, "AudioRecorder Thread");
        recordingThread.start();


    }

    private void writeAudioDataToBuffer() {
        short[] sData = new short[bufferElements2Rec];

        while (isRecording) {
            recorder.read(sData, 0, bufferElements2Rec);
            //Log.e("Record data", String.format("fData[100]:%f",sData[100]/(double)32768));
            //int e = circularBuffer.insertBuffer(sData);

            //short[] sData = circularBuffer.consumeBuffer();
            double[] fData = new double[sData.length];

            //int size = (int) pcmFile.length();
            //File pcmFile = new File(this.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Direct.pcm");
            byte[] bytes = new byte[bufferElements2Rec*2];
            try {
                InputStream buf = getResources().openRawResource(R.raw.direct);
                buf.read(bytes, 0, bufferElements2Rec*2);
                buf.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            short[] dData = byte2short(bytes);
            double[] fdData = new double[dData.length];

            Filter filter = new Filter(15900, 44100, Filter.PassType.Highpass, 1);
            Filter filter2 = new Filter(15900, 44100, Filter.PassType.Highpass, 1);
            for (int i = 0; i < sData.length; i++) {
                float data = sData[i] / (float) 32768;
                filter.Update(data);
                fData[i] = filter.getValue();

                float data2 = dData[i] / (float) 32768;
                filter2.Update(data2);
                fdData[i] = filter2.getValue();
                //fData[i] = sData[i] / (double)32768;
                //dData[i] = (short) (fData[i] * 32767);
            }

            signalProcessor.FourierTransform(sample,fdData, fData);
            predExp.post(new Runnable() {
                public void run() {
                    //predExp.setText((signalProcessor.exp)+"");
                    if((signalProcessor.exp+"").equals("Happy")) {
                        passIntent[0]++;
                        timeExp[tEctr][0]=0;
                        timeExp[tEctr][1]=(int)(System.currentTimeMillis() - startTime) / 1000;
                    }
                    if((signalProcessor.exp+"").equals("Sad") || (signalProcessor.exp+"").equals("Neutral")) {
                        passIntent[1]++;
                        timeExp[tEctr][0]=1;
                        timeExp[tEctr][1]=(int)(System.currentTimeMillis() - startTime) / 1000;
                    }
                    if((signalProcessor.exp+"").equals("Angry")) {
                        passIntent[2]++;
                        timeExp[tEctr][0]=2;
                        timeExp[tEctr][1]=(int)(System.currentTimeMillis() - startTime) / 1000;
                    }
                    if((signalProcessor.exp+"").equals("Surprise")) {
                        passIntent[3]++;
                        timeExp[tEctr][0] = 3;
                        timeExp[tEctr][1] = (int) (System.currentTimeMillis() - startTime) / 1000;
                    }
                    tEctr++;

                }
            });
            if(!FreqBinInitialized && iterator==POINTS_FOR_INITIAL){
                signalProcessor.initializeFreq_Bin();
//                freqBin.post(new Runnable() {
//                    public void run() {
//                        freqBin.setText((signalProcessor.FREQ_BIN)+"");
//                    }
//                });;
                FreqBinInitialized = true;
            }
            iterator = (iterator+1)%NUMBER_OF_POINTS;
        }
    }

    private byte[] short2byte(short[] sData) {
        int shortArrSize = sData.length;
        byte[] bytes = new byte[shortArrSize * 2];
        for (int i = 0; i < shortArrSize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;

    }

    private short[] byte2short(byte[] bytes) {
        int shortArrSize = bytes.length/2;
        short[] sData = new short[shortArrSize];
        for(int i=0; i<shortArrSize; i++) {
            short lsb = (short) (bytes[i*2]);
            short msb = (short) (bytes[i*2+1]);
            sData[i] = (short) ((msb << 8) + lsb);
        }
        return sData;
    }

    private void writeAudioDataToFile(String pcmFileName) {
        // Write the output audio in byte
        File pcmFile = new File(this.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Direct.pcm");
        String filePath = pcmFile.getAbsolutePath();
        Log.e("path",filePath);
        short sData[] = new short[bufferElements2Rec];
        FileOutputStream os;
        os = null;
        try {
            os = new FileOutputStream(filePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        while (isRecording) {
            // gets the voice output from microphone to byte format
            recorder.read(sData, 0, bufferElements2Rec);
            float[] dData = new float[sData.length];
            for(int i=0; i< sData.length; i++){
                dData[i] = sData[i] / (float)32768 ;
                sData[i] = (short) (dData[i] * 32767);
            }

            try {
                byte bData[] = short2byte(sData);
                os.write(bData, 0, bufferElements2Rec * bytesPerElement);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        // stops the recording activity
        if (null != recorder) {
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;
            processingThread = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        timeExp=new int[3600][3600];
        passIntent=new int[7];
        for(int i=0;i<7;i++)
            passIntent[i]=0;
        if (isNetwork(getApplicationContext())){

            Toast.makeText(getApplicationContext(), "Internet Connected", Toast.LENGTH_SHORT).show();

        } else {

            Toast.makeText(getApplicationContext(), "Internet Is Not Connected", Toast.LENGTH_SHORT).show();
        }
        predExp=(TextView)findViewById(R.id.predExp);
        ActivityCompat.requestPermissions(this,
                permissions,
                REQUEST_RECORD_AUDIO_PERMISSION);
        ActivityCompat.requestPermissions(this,
                permissions,
                WRITE_EXTERNAL_STORAGE_PERMISSION);

        audioTrack = new AudioTrack(AudioManager.STREAM_RING,
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, GENERATED_SOUND.length,
                AudioTrack.MODE_STATIC);

        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_NORMAL);
        audioManager.setSpeakerphoneOn(false);
        radioGroup = (RadioGroup) findViewById(R.id.radioGroup);
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                RadioButton checkedRadioButton = (RadioButton) findViewById(checkedId);
                String text = checkedRadioButton.getText().toString();
                Toast.makeText(getApplicationContext(), "Selected length is "+text, Toast.LENGTH_SHORT).show();
                if(text.equalsIgnoreCase("Short ")) {
                    passIntent[4] = 0;
                }
                if(text.equalsIgnoreCase("Medium ")) {
                    passIntent[4] = 1;
                }
                if(text.equalsIgnoreCase("Long ")) {
                    passIntent[4] = 2;
                }
                for (int i = 0; i < radioGroup.getChildCount(); i++) {
                    radioGroup.getChildAt(i).setEnabled(false);
                }
            }
        });


        mYouTubePlayerView = (YouTubePlayerView) findViewById(R.id.ytPlayer);

        //expression = findViewById(R.id.expression);
//        freqBin = findViewById(R.id.freqBin);
        thread=null;
        bhappy= (Button) findViewById(R.id.bHappy);
        bsad= (Button) findViewById(R.id.bSad);
        bangry= (Button) findViewById(R.id.bAngry);
        bsur= (Button) findViewById(R.id.bHorror);
        bmix=(Button) findViewById(R.id.bMixed);
        bhappy.setOnClickListener(view -> {
            bmix.setEnabled(false);
            bangry.setEnabled(false);
            bhappy.setEnabled(false);
            bsad.setEnabled(false);
            bsur.setEnabled(false);
            passIntent[5]=0;
            genTone();
            Thread playThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    playSound();


                }

            });
            playThread.start();
            mOnInitializedListener= new YouTubePlayer.OnInitializedListener() {
                @Override
                public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer youTubePlayer, boolean b) {

                    List<String> videoList=new ArrayList<>();
                    if(passIntent[4]==0)
                        videoList.add("yGqP54lv9q4");
                    if(passIntent[4]==1)
                        videoList.add("D-qj0L68RhQ");
                    if(passIntent[4]==2)
                        videoList.add("WYQXx1WDLGc");
                    youTubePlayer.loadVideos(videoList);

                }

                @Override
                public void onInitializationFailure(YouTubePlayer.Provider provider, YouTubeInitializationResult youTubeInitializationResult) {

                }
            };
            mYouTubePlayerView.initialize(API_KEY,mOnInitializedListener);
        });
        bsad.setOnClickListener(view -> {
            bmix.setEnabled(false);
            bangry.setEnabled(false);
            bhappy.setEnabled(false);
            bsad.setEnabled(false);
            bsur.setEnabled(false);
            passIntent[5]=1;
            genTone();
            Thread playThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    playSound();


                }
            });
            playThread.start();
            mOnInitializedListener= new YouTubePlayer.OnInitializedListener() {
                @Override
                public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer youTubePlayer, boolean b) {
                    List<String> videoList=new ArrayList<>();
                    if(passIntent[4]==0)
                        videoList.add("mV_w9Zv-TdM");
                    if(passIntent[4]==1)
                        videoList.add("r9SsqcT6heE");
                    if(passIntent[4]==2)
                        videoList.add("guay7W96oRo");
                    youTubePlayer.loadVideos(videoList);

                }

                @Override
                public void onInitializationFailure(YouTubePlayer.Provider provider, YouTubeInitializationResult youTubeInitializationResult) {

                }
            };
            mYouTubePlayerView.initialize(API_KEY,mOnInitializedListener);
        });
        bangry.setOnClickListener(view -> {
            bmix.setEnabled(false);
            bangry.setEnabled(false);
            bhappy.setEnabled(false);
            bsad.setEnabled(false);
            bsur.setEnabled(false);
            passIntent[5]=2;
            genTone();
            Thread playThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    playSound();


                }
            });
            playThread.start();
            mOnInitializedListener= new YouTubePlayer.OnInitializedListener() {
                @Override
                public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer youTubePlayer, boolean b) {
                    List<String> videoList=new ArrayList<>();
                    if(passIntent[4]==0)
                        videoList.add("KbFPdjkqUoA");
                    if(passIntent[4]==1)
                        videoList.add("oH5NjzDLK_k");
                    if(passIntent[4]==2)
                        videoList.add("P8lEOLAAU6U");
                    youTubePlayer.loadVideos(videoList);

                }

                @Override
                public void onInitializationFailure(YouTubePlayer.Provider provider, YouTubeInitializationResult youTubeInitializationResult) {

                }
            };
            mYouTubePlayerView.initialize(API_KEY,mOnInitializedListener);
        });
        bmix.setOnClickListener(view -> {
            bmix.setEnabled(false);
            bangry.setEnabled(false);
            bhappy.setEnabled(false);
            bsad.setEnabled(false);
            bsur.setEnabled(false);
            passIntent[5]=4;
            genTone();
            Thread playThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    playSound();


                }
            });
            playThread.start();
            mOnInitializedListener= new YouTubePlayer.OnInitializedListener() {
                @Override
                public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer youTubePlayer, boolean b) {
                    List<String> videoList=new ArrayList<>();
                    if(passIntent[4]==0)
                        videoList.add("aircAruvnKk");
                    if(passIntent[4]==1)
                        videoList.add("fq70UHD8DrM");
                    if(passIntent[4]==2)
                        videoList.add("IVCYjtsxvns");
                    youTubePlayer.loadVideos(videoList);

                }

                @Override
                public void onInitializationFailure(YouTubePlayer.Provider provider, YouTubeInitializationResult youTubeInitializationResult) {

                }
            };
            mYouTubePlayerView.initialize(API_KEY,mOnInitializedListener);
        });
        bsur.setOnClickListener(view -> {
            bmix.setEnabled(false);
            bangry.setEnabled(false);
            bhappy.setEnabled(false);
            bsad.setEnabled(false);
            bsur.setEnabled(false);
            passIntent[5]=3;
            genTone();
            Thread playThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    playSound();


                }
            });
            playThread.start();
            mOnInitializedListener= new YouTubePlayer.OnInitializedListener() {
                @Override
                public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer youTubePlayer, boolean b) {
                    List<String> videoList=new ArrayList<>();
                    if(passIntent[4]==0)
                        videoList.add("OxRIWBoluzs");
                    if(passIntent[4]==1)
                        videoList.add("h9E4SMwWaPw");
                    if(passIntent[4]==2)
                        videoList.add("NUyljxZnaJE&t=1093s");
                    youTubePlayer.loadVideos(videoList);

                }

                @Override
                public void onInitializationFailure(YouTubePlayer.Provider provider, YouTubeInitializationResult youTubeInitializationResult) {

                }
            };
            mYouTubePlayerView.initialize(API_KEY,mOnInitializedListener);
        });
        Button check = findViewById(R.id.check);
        check.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent = new Intent(MainActivity.this, Result.class);
                Bundle extras = new Bundle();
                extras.putString("result",  passIntent[0]+","+passIntent[1]+","+passIntent[2]+","+passIntent[3]+","+passIntent[4]+","+passIntent[5]);
                String curve="";
                for(int i=0;i<timeExp.length;i++)
                    curve=curve+timeExp[i][0]+":"+timeExp[i][1]+"\n";
                extras.putString("curve",  curve);

                intent.putExtras(extras);
                startActivity(intent);
            }
        });

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                startRecording();
            }
        }, 20);

        Button stopChirp = findViewById(R.id.stopChirp);
        stopChirp.setOnClickListener(view -> {
            audioTrack.stop();

            Handler handler2 = new Handler();
            handler2.postDelayed(new Runnable() {
                public void run() {
                    stopRecording();
                }
            }, 80);
        });
    }
    public boolean isNetwork(Context context) {

        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnectedOrConnecting()) {
            return true;
        }
        return false;
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioTrack != null)
            audioTrack.release();

        if(recorder != null)
            recorder.release();

        isRecording = false;
        audioManager.setMode(AudioManager.MODE_NORMAL);
    }
    public void playVid(){
        thread=new Thread(new Runnable() {
            @Override
            public void run() {
                audioManagerD = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
                audioManagerD.setMode(AudioManager.MODE_NORMAL);
                audioManagerD.setSpeakerphoneOn(false);
                videoView.start();

            }
        });
        thread.start();

    }


}