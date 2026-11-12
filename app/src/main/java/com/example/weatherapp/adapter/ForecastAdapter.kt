
package com.example.weatherapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.weatherapp.R
import com.example.weatherapp.model.ForecastDay

class ForecastAdapter(
    private val forecastList: List<ForecastDay>
) : RecyclerView.Adapter<ForecastAdapter.ForecastViewHolder>() {

    class ForecastViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        val day: TextView = itemView.findViewById(R.id.tvDay)

        val icon: TextView =
            itemView.findViewById(R.id.tvForecastIcon)

        val temperature: TextView =
            itemView.findViewById(R.id.tvMaxMin)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ForecastViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_forecast, parent, false)

        return ForecastViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ForecastViewHolder,
        position: Int
    ) {

        val item = forecastList[position]

        holder.day.text = item.day
        holder.icon.text = item.icon

        holder.temperature.text =
            "${item.maxTemp.toInt()}° / ${item.minTemp.toInt()}°"
    }

    override fun getItemCount(): Int {
        return forecastList.size
    }
}
