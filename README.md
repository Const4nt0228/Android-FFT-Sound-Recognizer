# 1줄 설명
스마트폰 마이크로 잡은 소리를 주파수로 변환(음계로)
정확도가 크게 좋지는 않음 C4 연주시 원래 대역대인 262만 잡혀야하는데 C5 대역대도 같이 잡힘
요거는 신디사이저에서 샘플링된 C4음계만 FFT 알고리즘에 넣어서 분석해도 동일한 현상이 나옴   
옥타브 구분은 살짝 힘들어도 어떤 음계인지는 정확히 판별가능


# Android-FFT-Sound-Recognizer (안드로이드 음계 인식기)

![image](https://user-images.githubusercontent.com/66546156/125195178-86850f80-e28f-11eb-838f-e11b6b069e66.png)

한국산업기술대학교 컴퓨터공학과 졸업작품 프로젝트 소프트웨어 주요 모듈중, 음계 인식 모듈

프로그램의 목적
-> 사용자가 연주하는 피아노 건반의 소리를 스마트폰 마이크로 인식하여 어떤 음을 쳤는지 알아내는 알아내는 프로그램
-> 피아노 연주 시 악보와 비교하여 내가 맞는 건반을 쳤는지 알 수 있음

사용 기술
-> RecordAudio, FFT(using RealDoubleFFT)

https://github.com/NoonChi-PIANO/noonchi-piano-rep


ca.uol.aig.fftpack.RealDoubleFFT와  JTransform의 DoubleFFT_1D 둘다 사용해 보았는데 
RealDoubleFFT의 성능이 조금 더 높기에 전자를 사용했다. 

현재 테스트 중인 피아노 대역대가 0에서 4096 까지이며 이렇게 된다면 1~7옥타브까지 측정이 가능하다

![image](https://user-images.githubusercontent.com/66546156/125195702-c0efac00-e291-11eb-99ba-d88d4db8c1a4.png)


line:40 의 frequency 가 8192인 이유는 마이크가 잡아내는 소리는 

![image](https://user-images.githubusercontent.com/66546156/125195732-ea103c80-e291-11eb-8895-19ed008c5413.png)

요런 형식으로 wave 형식으로 파동치는 형식이다. (그렇기 때문에 java version으로 마이크 없이 샘플 음만 테스트 하는 경우에는 .wav 확장자를 사용해서 한다.)
음수값으로 내려가는 (골짜기) 부분도 포함해서 소리 파형이 실수부 허수부로 나뉘기 때문에 실수부만 계산되면 대역대가 마이크가 잡아내는것의 딱 반만큼만 인식을 수행 할 수있다. 

일반적으로 wav 파일에서 쓰는 주파수 대역대가 44100Hz인데 만약 측정 장비와 하드웨어 성능, 스레드를 많이 돌릴수만 있다면 44100Hz로 frequency를 맞춰도된다. 

![image](https://user-images.githubusercontent.com/66546156/125195771-1af07180-e292-11eb-889f-f86eda2fae05.png)

FFT (Fast Fourier Transformtion)을 이용해서 이 소리의 파장을 특정 주파수만 찝어주면 된다

FFT는 wav 치는 소리의 파형을 높이 (진폭-> 소리의 높낮이)와 파장 (마루-마루 거리 or 골짜기-골짜기 -> 거리의 간격에 따라 음계의 높낮이가 결정)을 분석하여
Hz 주파수로 변환하게 한다. 각각 옥타브별 음계들의 Hz가 다 정해져 있으므로 측정한 Hz를 실시간으로 사용자에게 보여준다. 


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

AsyncTask를 이용해서 마이크를 동작시키는 RecordAudio가 비동기로 백그라운드에서 스레드로 돌아간다. 
그후 내부에서 FFT 연산을 수행해준뒤 출력되는 Double 형 배열을 표현 함수에서 사용자에게 보여주게 된다.

# Patch Not4e
0724 : Noise Suppressor 기능 추가. 얼마나 노이즈 잡힐지는..  
0822 : JTransform 으로 완전 이적함. 배열 갯수 4096으로 샘플링갯수와 동일함
1011 : Noise Suppressor 삭제, 1옥타브 대역대까지 같이 필터링되서 

