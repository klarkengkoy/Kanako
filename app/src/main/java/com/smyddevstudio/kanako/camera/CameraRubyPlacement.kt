package com.smyddevstudio.kanako.camera

import com.smyddevstudio.kanako.service.RubyRequest
import com.smyddevstudio.kanako.service.placeRubyReadings

internal fun placeCameraRubyReadings(requests: List<RubyRequest>, gap: Int) =
    placeRubyReadings(requests, gap)
