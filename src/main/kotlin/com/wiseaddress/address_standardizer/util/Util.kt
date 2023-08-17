package com.wiseaddress.address_standardizer.util

class Util {
    companion object {
        fun removeAndReturn(start: Int, end: Int, l: MutableList<Any>) : MutableList<Any> {
            for (i in end - 1 downTo start step 1) {
                l.removeAt(i)
            }
            return l
        }
    }

}