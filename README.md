# sound_Vanilla3
# NoonchiPIANO_FFT

![image](https://user-images.githubusercontent.com/66546156/125195178-86850f80-e28f-11eb-838f-e11b6b069e66.png)

한국산업기술대학교 컴퓨터공학과 졸업작품 프로젝트
소프트웨어 주요 모듈중, 음계 인식 모듈

Purpose of this program
-> 사용자가 연주하는 피아노 건반의 소리를 스마트폰 마이크로 인식하여 어떤 음을 쳤는지 알아내는 알아내는 프로그램
-> 피아노 연주 시 악보와 비교하여 내가 맞는 건반을 쳤는지 알 수 있음

사용 기술
-> RecordAudio, FFT (using RealDoubleFFT)

https://github.com/NoonChi-PIANO/noonchi-piano-rep


ca.uol.aig.fftpack.RealDoubleFFT와  JTransform의 DoubleFFT_1D 둘다 사용해 보았는데 
RealDoubleFFT의 성능이 조금 더 높기에 전자를 사용했다. 

현재 테스트 중인 피아노 대역대가 0~4096까지며 이렇게 된다면 0~7옥타브까지 측정이 가능핟

line:40 의 frequency 가 8192인 이유는 마이크가 잡아내는 소리는 

![image](https://user-images.githubusercontent.com/66546156/125195732-ea103c80-e291-11eb-8895-19ed008c5413.png)

요런 형식으로 wave 형식으로 파동치는 형식이다. 
그렇기 때문에 java version으로 마이크 없이 샘플 음만 테스트 하는 경우에는 .wav 확장자를 사용해서 한다.

![image](https://user-images.githubusercontent.com/66546156/125195702-c0efac00-e291-11eb-99ba-d88d4db8c1a4.png)
