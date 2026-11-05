package app.timeline.agentique.core

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** A localizable message: logic builds these, the UI resolves them. Keeps logic testable and i18n-ready. */
class UiText(@StringRes val id: Int, vararg val args: Any) {
    @Composable
    fun text(): String = stringResource(id, *args)

    fun resolve(ctx: Context): String = ctx.getString(id, *args)
}
