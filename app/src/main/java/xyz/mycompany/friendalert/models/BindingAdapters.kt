package xyz.mycompany.friendalert.models

import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.TextView
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide
import xyz.mycompany.friendalert.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@BindingAdapter("formattedDate")
fun formatContactDate(textView: TextView, timeInMillis: Long?) {
    timeInMillis?.let {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val date = Date(it)  // Convert Long to Date
        textView.text = sdf.format(date)
    } ?: run {
        textView.text = textView.context.getString(R.string.no_contact_date)
    }
}

@BindingAdapter("imageUri", "placeholder")
fun loadImage(view: ImageView, imageUri: String?, placeholder: Drawable?) {
    if (!imageUri.isNullOrEmpty()) {
        Glide.with(view.context)
            .load(imageUri)
            .placeholder(placeholder)
            .into(view)
    } else {
        view.setImageDrawable(placeholder)
    }
}