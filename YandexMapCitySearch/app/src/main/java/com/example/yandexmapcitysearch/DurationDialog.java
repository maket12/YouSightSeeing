package com.example.yandexmapcitysearch;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Диалог для показа времени прохождения маршрута
 */
public class DurationDialog extends Dialog {

    private double durationSeconds;

    public DurationDialog(Context context, double durationSeconds) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        this.durationSeconds = durationSeconds;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Контейнер с полупрозрачным фоном
        FrameLayout container = new FrameLayout(getContext());
        container.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        container.setBackgroundColor(Color.parseColor("#80000000"));

        // Карточка с информацией
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setLayoutParams(new FrameLayout.LayoutParams(
                dpToPx(300),
                dpToPx(80),
                Gravity.CENTER
        ));
        card.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        card.setGravity(Gravity.CENTER_VERTICAL);

        // Фон карточки белый с обводкой
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dpToPx(12));
        background.setStroke(dpToPx(1), Color.parseColor("#E0E0E0"));
        card.setBackground(background);

        // Текст времени маршрута
        TextView textDuration = new TextView(getContext());
        textDuration.setText(formatDuration(durationSeconds));
        textDuration.setTextSize(18);
        textDuration.setTextColor(Color.parseColor("#333333"));
        textDuration.setTypeface(null, android.graphics.Typeface.BOLD);
        textDuration.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        // Кнопка X для закрытия
        ImageView closeBtn = new ImageView(getContext());
        closeBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        closeBtn.setScaleType(ImageView.ScaleType.FIT_CENTER);
        closeBtn.setColorFilter(Color.parseColor("#999999"));
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(
                dpToPx(24),
                dpToPx(24)
        ));
        closeBtn.setOnClickListener(v -> dismiss());
        
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) closeBtn.getLayoutParams();
        params.setMargins(dpToPx(8), 0, 0, 0);
        closeBtn.setLayoutParams(params);

        // Добавляем элементы в карточку
        card.addView(textDuration);
        card.addView(closeBtn);
        
        // Добавляем карточку в контейнер
        container.addView(card);
        setContentView(container);

        setCanceledOnTouchOutside(true);
    }

    /**
     * Форматирует время в читаемый формат
     */
    private String formatDuration(double seconds) {
        if (seconds < 60) {
            return "⏱ Меньше минуты";
        } else if (seconds < 3600) {
            int minutes = (int) Math.round(seconds / 60);
            return "⏱ " + minutes + " мин";
        } else {
            int totalMinutes = (int) Math.round(seconds / 60);
            int hours = totalMinutes / 60;
            int minutes = totalMinutes % 60;

            if (minutes == 0) {
                return "⏱ " + hours + " ч";
            } else {
                return "⏱ " + hours + " ч " + minutes + " мин";
            }
        }
    }

    /**
     * Конвертирует dp в пиксели
     */
    private int dpToPx(int dp) {
        return (int) (dp * getContext().getResources().getDisplayMetrics().density);
    }
}
