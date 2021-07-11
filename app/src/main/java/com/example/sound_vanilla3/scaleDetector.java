package com.example.sound_vanilla3;

import android.Manifest;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import org.jtransforms.fft.DoubleFFT_1D;

import java.util.ArrayList;
import java.util.Iterator;

import ca.uol.aig.fftpack.RealDoubleFFT;

// FFT(Fast Fourier Transform) DFT 알고리즘 : 데이터를 시간 기준(time base)에서 주파수 기준(frequency base)으로 바꾸는데 사용.

public class scaleDetector extends Activity implements OnClickListener {

    int frequency = 8192; //주파수가 8192일 경우 4096 까지 측정이 가능함
    int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;

    private RealDoubleFFT transformer;
    int blockSize = 4096; // 2048->1024개의 배열이 나옴. 배열 한 칸당 4hz의 범위를 포함하고 있음.
    DoubleFFT_1D fft = new DoubleFFT_1D(blockSize); //JTransform 라이브러리로 FFT 수행

    String scale2 ;
    //frequency -> 측정 주파수 대역으로 퓨리에 변환 시 f/2 만큼의 크기의 주파수를 분석 할 수 있음.
    //blockSize -> 한 분기마다 측정하는 사이즈로 double 배열로 저장 시 , b/2 개의 배열이 나옴. f/b -> 배열 하나에 할당되는 주파수 범위로 8192/2048 -> 4Hz임

    Button startStopButton;

    boolean started = false;
    // RecordAudio는 여기에서 정의되는 내부 클래스로서 AsyncTask를 확장한다.
    RecordAudio recordTask;
    // Bitmap 이미지를 표시하기 위해 ImageView를 사용한다. 이 이미지는 현재 오디오 스트림에서 주파수들의 레벨을 나타낸다.
    // 이 레벨들을 그리려면 Bitmap에서 구성한 Canvas 객체와 Paint객체가 필요하다.

    ImageView imageView;
    Bitmap bitmap;
    Canvas canvas;
    Paint paint;

    BarChart chart;
    ArrayList xlabels = new ArrayList();
    ArrayList ylabels = new ArrayList();
    BarData data;

    TextView t0;
    TextView t1 ;
    TextView t2 ;


    //스레드 관련 부분 1
   // scaleThread scThr = new scaleThread();
      double[] mag = new double[blockSize/2];

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(R.layout.activity_scale);

        startStopButton = (Button) findViewById(R.id.StartStopButton);
        startStopButton.setOnClickListener(this);

        // RealDoubleFFT 클래스 컨스트럭터는 한번에 처리할 샘플들의 수를 받는다. 그리고 출력될 주파수 범위들의 수를 나타낸다.
        transformer = new RealDoubleFFT(blockSize);

        // ImageView 및 관련 객체 설정 부분
       // imageView = (ImageView) findViewById(R.id.ImageView01);
        bitmap = Bitmap.createBitmap((int) blockSize/2, (int) 200, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        paint = new Paint();
        paint.setColor(Color.GREEN);
      //  imageView.setImageBitmap(bitmap);

        t0 = (TextView)findViewById(R.id.HzText0);
        t1 = (TextView)findViewById(R.id.HzText1);
        t2 = (TextView)findViewById(R.id.HzText2);


        chart =(BarChart)findViewById(R.id.chart);
        YAxis leftYAxis = chart.getAxisLeft();
        leftYAxis.setAxisMaxValue((float)200);
        leftYAxis.setAxisMinValue(0);
        chart.getAxisRight().setEnabled(false);

        //chart 그리기
        int xChart=0;
        //x축 라벨 추가
        //4096 / 16 =256 씩 16칸으로 할거임
        for(int i=0; i<1024; i++){
            xlabels.add(Integer.toString(xChart));
            xChart=xChart+1;
        }

        ylabels.add(new BarEntry(2.2f,0));
        ylabels.add(new BarEntry(10f,512));
        ylabels.add(new BarEntry(63.f,800));
        ylabels.add(new BarEntry(70.f,900));

        BarDataSet barDataSet = new BarDataSet(ylabels,"Hz");
      //  chart.animateY(5000);
        data = new BarData(xlabels,barDataSet); //MPAndroidChart v3.1 에서 오류나서 다른 버전 사용
       // barDataSet.setColors(ColorTemplate.COLORFUL_COLORS);

        chart.setData(data);

        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(scaleDetector.this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }
        //오디오 녹음을 사용할 것인지 권한 여부를 체크해주는 코드로, 없으면 동작 안됨! +) AndroidManifest에도 오디오 권한 부분 추가되있음
    }

    // 이 액티비티의 작업들은 대부분 RecordAudio라는 클래스에서 진행된다. 이 클래스는 AsyncTask를 확장한다.
    // AsyncTask를 사용하면 사용자 인터페이스를 멍하니 있게 하는 메소드들을 별도의 스레드로 실행한다.
    // doInBackground 메소드에 둘 수 있는 것이면 뭐든지 이런 식으로 실행할 수 있다.

    private class RecordAudio extends AsyncTask<Void, double[], Void> {

        //스레드 관련 부분 2
       // scaleThread scThread = new scaleThread();

        @Override
        protected Void doInBackground(Void... params) {
            try {
                // AudioRecord를 설정하고 사용한다.
                int bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
                AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, frequency, channelConfiguration, audioEncoding, bufferSize);
                // short로 이뤄진 배열인 buffer는 원시 PCM 샘플을 AudioRecord 객체에서 받는다.
                // double로 이뤄진 배열인 toTransform은 같은 데이터를 담지만 double 타입인데, FFT 클래스에서는 double타입이 필요해서이다.
                short[] buffer = new short[blockSize];
                double[] toTransform = new double[blockSize];
               // double[] mag = new double[blockSize/2];

                audioRecord.startRecording();

                while (started) {
                    int bufferReadResult = audioRecord.read(buffer, 0, blockSize);

                    //FFT는 Double 형 데이터를 사용하므로 short로 읽은 데이터를 형변환 시켜줘야함. short / short.MAX_VALUE = double
                    for (int i = 0; i < blockSize && i < bufferReadResult; i++) {
                        toTransform[i] = (double) buffer[i] / Short.MAX_VALUE; // 부호 있는 16비트
                    }


                    //두개의 FFT 코드를 사용 잡음잡는것은 RealDoubleFFT가 훨씬 더 잘잡는다.
                    //RealDoubleFFT 부분
                    //transformer.ft(toTransform);

                    //-> JTransform 부분
                    //Jtransform 은 입력에 실수부 허수부가 들어가야하므로 허수부 임의로 0으로 채워서 생성해줌
                    double y[] = new double[blockSize];
                    for (int i = 0; i < blockSize; i++) {
                        y[i] = 0;
                    }
                    //실수 허수를 넣으므로 연산에는 blockSize의 2배인 배열 필요
                    double[] summary = new double[2 * blockSize];
                    for (int k = 0; k < blockSize; k++) {
                        summary[2 * k] = toTransform[k]; //실수부
                        summary[2 * k + 1] = y[k]; //허수부 0으로 채워넣음.
                    }
                  //  DoubleFFT_1D fft = new DoubleFFT_1D(blockSize);
                    fft.complexForward(summary);
                    for(int k=0;k<blockSize/2;k++){
                        mag[k] = Math.sqrt(Math.pow(summary[2*k],2)+Math.pow(summary[2*k+1],2));
                    }


                    // publishProgress를 호출하면 onProgressUpdate가 호출된다.
                    //publishProgress(toTransform);

                    //스레드를 여기에 넣어야할 듯?
                    // scThread.start();
                    publishProgress(mag);
                }
                audioRecord.stop();
            } catch (Throwable t) {
                Log.e("AudioRecord", "Recording Failed");
            }
            return null;
        }
        // onProgressUpdate는 우리 엑티비티의 메인 스레드로 실행된다. 따라서 아무런 문제를 일으키지 않고 사용자 인터페이스와 상호작용할 수 있다.
        // 이번 구현에서는 onProgressUpdate가 FFT 객체를 통해 실행된 다음 데이터를 넘겨준다. 이 메소드는 최대 100픽셀의 높이로 일련의 세로선으로

        @Override
        protected void onProgressUpdate(double[]... toTransform) {
            //차트 삭제 부분
            xlabels.clear();
            ylabels.clear();

            int xChart=0;
            for(int i=0; i<toTransform[0].length; i++){
                xlabels.add(Integer.toString(xChart));
                xChart=xChart+1;
            }

            for(int i=0; i<toTransform[0].length; i++){
                ylabels.add(new BarEntry((float)toTransform[0][i],i));
            }


            ArrayList<Integer> hzList = new ArrayList<Integer>();
            ArrayList<Double> hzSize = new ArrayList<Double>();

            for(int i=0; i<toTransform[0].length; i++){
                if(toTransform[0][i]>45){
                    hzList.add(i);   //list에는 대역대가들어감 배열 i 순서
                    hzSize.add(toTransform[0][i]); //list에는 toTransform[][i]의 안에있는 값(크기) 가들어감
                }
            }

            Iterator iter = hzList.iterator();
            if(iter.hasNext()==true){
                t0.setText(Integer.toString(hzList.get(0)*2)); //대역대
                t1.setText(Double.toString(hzSize.get(0)));      //소리 크기
                //t2.setText(whichScale(hzList.get(0)*4));
                t2.setText(whichScale2(toTransform));
            }

            //생각해보니까 Transform 배열 전체를 다 넘겨준다음에. double[]... toTransform
            //거기 함수 안에서 if(도) toTransform[262] 값이랑 뭐뭐 200이상 뛰면으로 바꾸면될거같음

            hzSize.clear();
            hzList.clear();

            //차트 없애는 부분 여기
            BarDataSet barDataSet = new BarDataSet(ylabels,"Hz");
            data = new BarData(xlabels,barDataSet);

            chart.setData(data);
            chart.invalidate();

        }
    }

    public String whichScale2(double[]... toTransform){

        if(toTransform[0][111]>99999){

        }
        else if(toTransform[0][32]>55 ||toTransform[0][33]>55  ){
            scale2 = "C"; //도
        }
        else if(toTransform[0][73]>45 ||toTransform[0][74]>45){
            scale2 = "D"; //레
        }
        else if(toTransform[0][41]>33 ){
            scale2 = "E";
        }
        else if(toTransform[0][75]>55 || toTransform[0][86]>55 || toTransform[0][87]>55){
            scale2 = "F";
        }
        else if(toTransform[0][48]>55 || toTransform[0][49]>55){
            scale2 = "G";
        }
        else if( toTransform[0][55]>35){
            scale2 = "A";
        }
        else if(toTransform[0][62]>45 || toTransform[0][61]>45  ){
            scale2 = "B";
        }

        else if(toTransform[0][66]>35 || (toTransform[0][131]>45&&toTransform[0][131]<139)){
            scale2 ="C"; //도
        }
        else if(toTransform[0][73]>55 || toTransform[0][61]>45 || toTransform[0][74]>45){
            scale2 = "D"; //레
            //74는 F4랑 겹쳐서 일단 뺌
        }
        else if(toTransform[0][70]>45 ){
            scale2 = "E"; //미
        }
        else if(toTransform[0][87]>55 ||toTransform[0][86]>55 ){
            scale2 = "F"; //파
        }
        else if((toTransform[0][98]>55 &&toTransform[0][97]>33) ||  toTransform[0][96]>55 || toTransform[0][85]>55 ){
            scale2 = "G"; //솔
            //솔 98 음 인식 좀 이상함
        }
        else if(toTransform[0][110]>110 ){
            scale2 = "A"; //라
        }
        else if(toTransform[0][123]>80 || toTransform[0][124] >80  ){
            scale2 = "B"; //시
        }

        else if(toTransform[0][130]>45 || toTransform[0][131]>120 || toTransform[0][118]>45 || toTransform[0][119]>45 ){
            scale2 = "C";
        }
        else if(toTransform[0][147]>45 || toTransform[0][135]>45){
            scale2 = "D";
        }
        else if(toTransform[0][165]>45 || toTransform[0][153]>45){
            scale2 = "E";
        }
        else if(toTransform[0][174]>45 || toTransform[0][175]>45 || toTransform[0][176]>45 || toTransform[0][163]>45 ){
            scale2 = "F";
        }
        else if((toTransform[0][171]>45 && toTransform[0][184]>40) || (toTransform[0][184]>45&& toTransform[0][184]>40)
            || (toTransform[0][196]>45 && toTransform[0][184]>40)){
            scale2 = "G";
        }
        else if(toTransform[0][221]>60 || toTransform[0][220]>60 || toTransform[0][208]>60){
            scale2 = "A";
        }
        else if(toTransform[0][223]>45 || toTransform[0][235]>45 || toTransform[0][247]>45 || toTransform[0][248]>45){
            scale2 = "B";
        }

        else if(toTransform[0][34]>33 || toTransform[0][35]>33){
            scale2 = "C3#";
        }
        else if(toTransform[0][39]>33){
            scale2 = "D3#";
        }
        else if(toTransform[0][46]>33){
            scale2 = "F3#";
        }
        else if(toTransform[0][52]>33 || toTransform[0][51]>33 || toTransform[0][50]>33){
            scale2 = "G3#";
        }
        else if(toTransform[0][58]>33){
            scale2 = "A3#";
        }
        else if(toTransform[0][64]>33 || toTransform[0][69]>33){
            scale2 = "C4#";
        }
        else if(toTransform[0][79]>33 || toTransform[0][78]>33){
            scale2 = "D4#";
        }
        else if(toTransform[0][92]>33){
            scale2 = "F4#";
        }
        else if(toTransform[0][104]>33){
            scale2 = "G4#";
        }
        else if(toTransform[0][117]>33 || toTransform[0][116]>33){
            scale2 = "A4#";
        }
        else if(toTransform[0][139]>33 ){
            scale2 = "C5#";
        }
        else if(toTransform[0][155]>33 || toTransform[0][156]>33){
            scale2 = "D5#";
        }
        else if(toTransform[0][185]>33 || toTransform[0][186]>33){
            scale2 = "F5#";
        }
        else if(toTransform[0][208]>33 || toTransform[0][209]>33  || toTransform[0][207]>33  ){
            scale2 = "G5#";
        }
        else if(toTransform[0][233]>33 || toTransform[0][234]>33 ){
            scale2 = "A5#";
        }

        else{

        }

        return scale2;
    }

   /* public String whichScale(int k){
        String scale="dib";

        if(k>250 && k<270 ){
            scale ="C4"; //도
        }else if(k>286 && k<302){
            scale = "D4"; //레
        }else if(k>320 && k<340)
        {
            scale = "E4"; //미
        } else if(k>378 && k<403){
            scale ="G4"; //솔
        }else if(k>427 && k<452){
            scale = "A4"; //라
        }
        else if(k>508&&k<538){
            scale ="C5"; //도
        }
        else{
            scale = "no";
        }

        return scale;
    }*/

    @Override
    public void onClick(View arg0) {
        if (started) {
            started = false;
            startStopButton.setText("Start");
            recordTask.cancel(true);
        } else {
            started = true;
            startStopButton.setText("Stop");
            recordTask = new RecordAudio();
            recordTask.execute();
        }
    }

    private class scaleThread extends Thread{
        RecordAudio rcAu = new RecordAudio();

        public scaleThread(){

        }
        public void run(){
            rcAu.onProgressUpdate(mag);

        }
    }


}//activity

