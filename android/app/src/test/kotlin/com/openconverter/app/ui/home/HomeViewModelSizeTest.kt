package com.openconverter.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelSizeTest {
    @Test fun positive_size_passes_through() = assertEquals(4481054L, HomeViewModel.mapSize(4481054L))
    @Test fun zero_becomes_unknown() = assertEquals(-1L, HomeViewModel.mapSize(0L))
    @Test fun negative_becomes_unknown() = assertEquals(-1L, HomeViewModel.mapSize(-1L))
}
