package com.github.shadowsocks.bg

import android.os.Parcel
import android.os.Parcelable

data class Benchmark(val profileId: Int,
                     val delay: Long): Parcelable {
    constructor(parcel: Parcel) : this(
            parcel.readInt(),
            parcel.readLong()) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(profileId)
        parcel.writeLong(delay)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Benchmark> {
        override fun createFromParcel(parcel: Parcel): Benchmark {
            return Benchmark(parcel)
        }

        override fun newArray(size: Int): Array<Benchmark?> {
            return arrayOfNulls(size)
        }
    }
}