package com.example.voiptest

import org.pjsip.pjsua2.AudioMediaPort
import org.pjsip.pjsua2.ByteVector
import org.pjsip.pjsua2.MediaFrame
import org.pjsip.pjsua2.pjmedia_frame_type

/**
 * PJSIP의 AudioMediaPort를 상속받는 커스텀 미디어 포트.
 * 컨퍼런스 브리지로부터 수신된 원시 오디오 프레임(ByteArray)을
 * 생성자에서 전달받은 리스너(람다)를 통해 외부로 전달합니다.
 *
 * @param listener 오디오 프레임이 수신될 때마다 호출될 콜백 함수. (ByteArray) -> Unit 타입.
 */
class SttAudioPort(private val listener: (frame: ByteVector) -> Unit) : AudioMediaPort() {

    /**
     * 컨퍼런스 브리지로부터 오디오 프레임이 수신될 때 호출되는 콜백 메소드.
     */
    override fun onFrameReceived(frame: MediaFrame) {
        // 프레임 타입이 오디오인지 확인합니다.
        if (frame.type == pjmedia_frame_type.PJMEDIA_FRAME_TYPE_AUDIO) {
            // MediaFrame에서 원시 오디오 데이터를 ByteArray로 가져옵니다.
            val audioData = frame.buf

            // 생성자에서 전달받은 리스너를 호출하여 오디오 데이터를 전달합니다.
            // 주의: 이 콜백은 실시간 오디오 스레드에서 실행되므로,
            // 이 람다의 구현은 절대 Block 되어서는 안 됩니다.
            listener(audioData)
        }
    }
}