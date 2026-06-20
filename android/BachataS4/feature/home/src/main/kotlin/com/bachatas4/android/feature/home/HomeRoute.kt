package com.bachatas4.android.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bachatas4.android.data.SampleRepository
import javax.inject.Inject

class HomeViewModel @Inject constructor(
    private val repo: SampleRepository,
) {
    val text: String = repo.greeting()
}

@Composable
fun HomeRoute() {
    val vm = HomeViewModel(SampleRepository())
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(vm.text)
    }
}
