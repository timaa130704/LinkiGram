 
package app.nimarkogram.messenger.utils.ui

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import app.nimarkogram.messenger.NimarkoConfig
import app.nimarkogram.messenger.appearance.LinkiGramYouTheme
import app.nimarkogram.messenger.xcfg.XcfgController
import app.nimarkogram.messenger.xcfg.XcfgPrefs
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.glass.GlassTabView

object MainTabsManager {

    enum class TabType {
        CHATS,
        SETTINGS,
        PROFILE,
        SEARCH,
        CONTACTS,
        CALLS,
        XCFG
    }

    data class Tab(
        var type: TabType,

        @JvmField
        var enabled: Boolean
    )

    @JvmOverloads
    fun getEnabledTabs(includeSearch: Boolean = false): List<Tab> {
        val enabled = loadTabs().filter { it.enabled }
        val result = if (includeSearch) {
            enabled
        } else {
            enabled.filterNot { it.type == TabType.SEARCH }
        }
        
        return if (result.any { it.type == TabType.CHATS }) {
            result
        } else {
            listOf(Tab(TabType.CHATS, true)) + result
        }
    }

    fun getAllTabs(): List<Tab> {
        return loadTabs()
    }

    private fun loadTabs(): MutableList<Tab> {
        val you = LinkiGramYouTheme.isEnabled()
        val allPossibleTypes = (if (you) TabType.entries else listOf(
            TabType.PROFILE, TabType.CHATS, TabType.SETTINGS, TabType.SEARCH)).toMutableList()
        val value = if (you) NimarkoConfig.getPreferences().getString("youMainTabsOrder", null) else NimarkoConfig.mainTabsOrder

        val result = mutableListOf<Tab>()

        if (value == null) {
            if (you) {
                result.addAll(listOf(Tab(TabType.CONTACTS, true), Tab(TabType.XCFG, XcfgPrefs.getBool("show_tab", true)),
                    Tab(TabType.CHATS, true), Tab(TabType.SETTINGS, true), Tab(TabType.PROFILE, true),
                    Tab(TabType.CALLS, UserConfig.getInstance(UserConfig.selectedAccount).showCallsTab),
                    Tab(TabType.SEARCH, NimarkoConfig.showSearchInTabs)))
            } else {
                result.add(Tab(TabType.PROFILE, true))
                result.add(Tab(TabType.CHATS, true))
                result.add(Tab(TabType.SETTINGS, true))
            }
        } else {
            val parts = value.split(",")

            for (p in parts) {
                val enabled = !p.startsWith("!")
                val typeName = if (enabled) p else p.substring(1)

                try {
                    val type = TabType.valueOf(typeName)
                    if (allPossibleTypes.remove(type)) {
                        result.add(Tab(type, if (type == TabType.CHATS) true else enabled))
                    }
                } catch (_: Exception) {
                }
            }

            for (newType in allPossibleTypes) {
                result.add(Tab(newType, if (you) newType != TabType.CALLS && newType != TabType.SEARCH else true))
            }
        }

        return result
    }

    fun createTabView(
        context: Context,
        resourceProvider: Theme.ResourcesProvider?,
        currentAccount: Int,
        type: TabType,
        fromSettings: Boolean,
        showSearch: Boolean
    ): GlassTabView {
        return when (type) {
            TabType.CHATS -> GlassTabView.createMainTab(
                context,
                resourceProvider,
                GlassTabView.TabAnimation.CHATS,
                R.string.MainTabsChats
            )

            TabType.SETTINGS -> {
                if (!hasTab(TabType.PROFILE) && !fromSettings) {
                    GlassTabView.createAvatar(
                        context,
                        resourceProvider,
                        currentAccount,
                        R.string.Settings
                    )
                } else {
                    GlassTabView.createMainTab(
                        context,
                        resourceProvider,
                        GlassTabView.TabAnimation.SETTINGS,
                        R.string.Settings
                    )
                }
            }

            TabType.PROFILE -> GlassTabView.createAvatar(
                context,
                resourceProvider,
                currentAccount,
                R.string.MainTabsProfile
            )

            TabType.SEARCH -> {
                if (showSearch) {
                    GlassTabView.createMainTab(
                        context,
                        resourceProvider,
                        GlassTabView.TabAnimation.SEARCH,
                        R.string.Search
                    )
                } else {
                    GlassTabView(context).apply {
                        visibility = View.GONE
                        layoutParams = RecyclerView.LayoutParams(0, 0)
                    }
                }
            }
            TabType.CONTACTS -> GlassTabView.createMainTab(context, resourceProvider,
                GlassTabView.TabAnimation.CONTACTS, R.string.MainTabsContacts)
            TabType.CALLS -> GlassTabView.createMainTab(context, resourceProvider,
                GlassTabView.TabAnimation.CALLS, R.string.MainTabsCalls)
            TabType.XCFG -> GlassTabView.createMainTab(context, resourceProvider,
                GlassTabView.TabAnimation.LINK, R.string.Settings).apply {
                    setText("X-CFG")
                    setTabIcon(XcfgController.tabIcon(context))
                }
        }
    }

    fun getPosition(type: TabType): Int {
        val tabs = getEnabledTabs()
        for (i in tabs.indices) {
            if (tabs[i].type == type) {
                return i
            }
        }
        return -1
    }

    fun hasTab(type: TabType): Boolean {
        return getPosition(type) != -1
    }

    fun saveTabs(tabs: List<Tab>) {
        val seen = mutableSetOf<TabType>()
        val normalized = tabs.filter { seen.add(it.type) }.toMutableList()
        if (normalized.none { it.type == TabType.CHATS }) normalized.add(0, Tab(TabType.CHATS, true))
        normalized.first { it.type == TabType.CHATS }.enabled = true
        if (!LinkiGramYouTheme.isEnabled()) normalized.firstOrNull { it.type == TabType.SEARCH }?.let {
            normalized.remove(it)
            normalized.add(it)
        }
        val order = normalized.map { tab ->
            val prefix = if (tab.enabled || tab.type == TabType.CHATS) "" else "!"
            "$prefix${tab.type.name}"
        }
        if (LinkiGramYouTheme.isEnabled()) {
            NimarkoConfig.getPreferences().edit().putString("youMainTabsOrder", order.joinToString(",")).apply()
            normalized.firstOrNull { it.type == TabType.SEARCH }?.let {
                NimarkoConfig.showSearchInTabs = it.enabled
                NimarkoConfig.getPreferences().edit().putBoolean("showSearchInTabs", it.enabled).apply()
            }
        } else {
            NimarkoConfig.setMainTabsOrder(order.joinToString(","))
        }
    }

    fun resetTabs() {
        if (LinkiGramYouTheme.isEnabled()) NimarkoConfig.getPreferences().edit().remove("youMainTabsOrder").apply()
        else NimarkoConfig.setMainTabsOrder(null)
    }

}
