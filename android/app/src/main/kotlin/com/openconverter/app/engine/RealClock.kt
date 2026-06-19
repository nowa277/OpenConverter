package com.openconverter.app.engine

/** Real wall-clock for production wiring. */
class RealClock : Clock { override fun nowMs(): Long = System.currentTimeMillis() }
