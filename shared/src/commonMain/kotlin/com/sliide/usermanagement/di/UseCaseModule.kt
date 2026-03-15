package com.sliide.usermanagement.di

import com.sliide.usermanagement.domain.usecase.GetUserDetailUseCase
import com.sliide.usermanagement.domain.usecase.GetUsersUseCase
import org.koin.dsl.module

val useCaseModule = module {
    factory { GetUsersUseCase(get()) }
    factory { GetUserDetailUseCase(get()) }
}
