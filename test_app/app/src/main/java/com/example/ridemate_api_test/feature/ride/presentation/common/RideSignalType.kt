package com.example.ridemate_api_test.feature.ride.presentation.common

import com.example.ridemate_api_test.R

/**
 * RIDE_SIGNAL 메시지의 signal 값과 표시 문구를 매핑한다.
 * LEADER_CHANGE 는 후미가 수신하는 선두 변경 요청이다.
 */
enum class RideSignalType(val signalValue: String, val labelRes: Int) {
    ROAD_HAZARD("ROAD_HAZARD", R.string.signal_hazard),
    CURVE_CAUTION("CURVE_CAUTION", R.string.signal_curve),
    LEFT("LEFT", R.string.signal_left),
    RIGHT("RIGHT", R.string.signal_right),
    SINGLE_FILE("SINGLE_FILE", R.string.signal_single),
    DOUBLE_FILE("DOUBLE_FILE", R.string.signal_double),
    SLOW("SLOW", R.string.signal_slow),
    STOP("STOP", R.string.signal_stop),
    LEADER_CHANGE("LEADER_CHANGE", R.string.signal_leader_change),
}
