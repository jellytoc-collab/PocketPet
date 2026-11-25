package com.pocketpet.data

/**
 * 이용 등급. 등급마다 쓸 수 있는 기능이 다르다.
 * 지금은 시험용 스위치로 바꾸고, 나중에 구글 결제(월 구독)와 연결한다.
 */
enum class Tier {
    FREE,       // 일반: 시로·쿠로만, 광고 있음
    VIP,        // 펫 전부 + 던지기, 앱 복귀 광고 없음 (배너는 있음)
    VIP_PLUS;   // VIP 전부 + 배너 광고 없음 + 대사·잠자기·여러 마리 소환

    /** 잠긴 펫(시로·쿠로 말고 다른 펫)을 쓸 수 있는지 */
    val allPets: Boolean get() = this != FREE

    /** 펫을 던질 수 있는지 (일반은 놓으면 그냥 떨어진다) */
    val canThrow: Boolean get() = this != FREE

    /** 폰을 흔들면 펫이 날아다니는지 (던지기와 같은 VIP 기능) */
    val canShakeFly: Boolean get() = this != FREE

    /** 앱에 돌아왔을 때 광고를 보여 줄지 */
    val showAppOpenAd: Boolean get() = this == FREE

    /** 화면 아래 배너 광고를 보여 줄지 */
    val showBanner: Boolean get() = this != VIP_PLUS

    /** VIP+ 전용 기능 (대사·잠자기·여러 마리 소환) */
    val plusFeatures: Boolean get() = this == VIP_PLUS

    companion object {
        fun fromName(name: String?): Tier = entries.firstOrNull { it.name == name } ?: FREE
    }
}
