package app.aaps.plugins.pebble.di

import android.content.Context
import androidx.preference.PreferenceManager
import app.aaps.plugins.pebble.IPebbleTransport
import app.aaps.plugins.pebble.PebbleFragment
import app.aaps.plugins.pebble.PebbleTransportImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import javax.inject.Singleton

@Module(includes = [PebbleModule.Binding::class, PebbleModule.Provide::class])
abstract class PebbleModule {

    @ContributesAndroidInjector
    abstract fun contributesPebbleFragment(): PebbleFragment

    @Module
    interface Binding {
        @Binds
        fun bindPebbleTransport(pebbleTransportImpl: PebbleTransportImpl): IPebbleTransport
    }

    @Module
    class Provide {
    }
}
