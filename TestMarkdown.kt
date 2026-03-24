import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.material3.Material3RichText
import androidx.compose.runtime.Composable

@Composable
fun Test() {
    Material3RichText {
        Markdown("Hello")
    }
}
