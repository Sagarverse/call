package com.example.call.ui.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.call.ui.dialer.DialerFragment
import com.example.call.ui.logs.CallLogsFragment
import com.example.call.ui.contacts.ContactsFragment
import com.example.call.ui.stats.CallStatsFragment

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> CallLogsFragment()
            1 -> DialerFragment()
            2 -> ContactsFragment()
            3 -> CallStatsFragment()
            else -> CallLogsFragment()
        }
    }
}
