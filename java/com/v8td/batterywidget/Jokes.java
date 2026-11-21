package com.v8td.batterywidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

/** Шуточные окна подтверждения для фулл-хаоса.
 *  Правила: Back и тап мимо = «нет»; кнопки «да»/«нет» ездят местами
 *  не более 3–4 раз и замирают; под пальцем не двигаются;
 *  надпись не врёт: кнопка двигается вместе со своим текстом.
 *  При выключенном фулл-хаосе окна обычные. */
final class Jokes {

    private static final String[] QUESTIONS = {
            "Да? Нет? Решайся!",
            "Подтвердить? Обратная дорога есть, но не обещана.",
            "Подтвердить? Последствия обратимы и слегка забавны. Или нет?",
            "Начнём? Порядок всё равно переоценён.",
            "Начнём? Цвета обещают вести себя плохо. Как ты любишь.",
            "Подтвердить? Ничего страшного не случится. Вероятно.",
            "Подтвердить? Обратимо: да. Или...?",
            "Начинаем? Будет весело. Возможно.",
            "Начнём? Цвета теперь будут жить по новым правилам: без правил!",
            "Начнём? Это легальный способ сделать пёстро.",
            "Начнём? Цвета готовы к внеплановой проверке.",
            "Да? Нет? Немного хаоса не повредит. Жми!",
            "Подтвердить? Действие из списка разрешённых радостей.",
            "Начинаем? Хаос не гарантирует ничего!",
            "Начнём? Хаос — это не беспорядок, это стиль.",
            "Действие безвредно для батареи и полезно для настроения.",
            "Подтвердить? Всё обратимо, включая эту фразу.",
            "Начинаем? Хаос точен: ломает только скуку.",
            "Подтвердить? Действие из разряда «весело и обратимо».",
            "Да или Нет? Цвета готовы встать в новый порядок. Или не в порядок.",
            "Порядок и хаос — просто соседи. Определяйся!",
            "Действие не повлияет на погоду. Соглашайся!",
            "Начнём? Это согласие на цветную случайность.",
            "Одобрят ли соседи этот оттенок?",
            "Ты точно этого хочешь?",
            "Нажмёшь «Да» — и мир уже не будет прежним. Рискнёшь?",
            "Осторожно: за кнопкой «Да» скрывается цветной беспредел. Жмём?",
            "А что, если нажать «Да»? Просто ради науки.",
            "Последний шанс сохранить адекватную цветовую гамму. Берёшь?",
            "Серьёзно? Прямо сейчас? Без подготовки?",
            "Рука не дрогнет? Решайся!",
            "Да? Нет? Не тяни, цвета сами себя не перемешают!",
            "Спорим, ты всё равно нажмёшь «Да»?",
            "Хватит смотреть на кнопки, пора творить хаос!",
            "Ну же, ткни уже куда-нибудь!",
            "Кнопка «Нет» для скучных. Выбор за тобой.",
            "Ну кого ты пытаешься обмануть? Жми уже!",
            "Внимание, запуск цветового апокалипсиса. Подтверждаешь?",
            "Осторожно: кнопка «Да» содержит следы тяжёлого психодела и блёсток.",
            "Ну давай, покажи миру, что чувство вкуса — это необязательно!",
            "Сделай лицо попроще. Жми «Да»!",
            "Если нажать «Нет», ничего не случится. Вообще ничего. Даже неинтересно.",
            "Секретный протокол «Вырвиглаз-2000» ждёт активации. Запускаем?",
            "Нажми «Да», если не боишься увидеть запрещённые сочетания цветов.",
            "Спорим, после этого на виджет будут смотреть чаще?",
            "Руки чешутся? Давай, не стесняйся.",
            "Осторожно: вызывает привыкание к пёстроте.",
            "Жми «Да», пока здравый смысл не вернулся.",
            "Последнее предупреждение: будет ярко.",
            "Это будет самое безобидное безумие за сегодня.",
            "Спорим, соседи по парковке одобрят?",
            "Это не баг, это арт-проект. Запускаем?",
            "Давай, удиви сам себя.",
            "Последний шанс отказаться. Или нет?",
            "Жми «Да» и считай до трёх. Раз, два…",
            "Это будет твой маленький цветной бунт.",
            "Спорим, завтра ты включишь это снова?",
            "Один тап — и никаких правил.",
            "Давай, раскрасим этот серый мир.",
            "Это легальный способ побыть немного хулиганом."
    };

    private Jokes() {
    }

    /** Подтверждение действия. При включённом фулл-хаосе — шуточное окно. */
    static void confirm(Activity a, String title, String message,
                        String positiveLabel, Runnable onYes) {
        if (!Prefs.fullChaos(a)) {
            new AlertDialog.Builder(a)
                    .setTitle(title)
                    .setMessage(message)
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton(positiveLabel, (d, w) -> onYes.run())
                    .show();
            return;
        }

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(a, 20), dp(a, 12), dp(a, 20), dp(a, 8));

        TextView joke = new TextView(a);
        joke.setText(QUESTIONS[new Random().nextInt(QUESTIONS.length)]);
        joke.setTextSize(16);
        joke.setTextColor(a.getColor(R.color.text));
        box.addView(joke, new LinearLayout.LayoutParams(-1, -2));

        TextView real = new TextView(a);
        real.setText(message);
        real.setTextSize(12);
        real.setTextColor(a.getColor(R.color.muted));
        real.setPadding(0, dp(a, 10), 0, 0);
        box.addView(real, new LinearLayout.LayoutParams(-1, -2));

        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = dp(a, 18);

        final Button yes = button(a, "Да", true);
        final Button no = button(a, "Нет", false);
        final LinearLayout.LayoutParams lpY = new LinearLayout.LayoutParams(0, dp(a, 44), 1f);
        lpY.setMargins(dp(a, 2), 0, dp(a, 2), 0);
        final LinearLayout.LayoutParams lpN = new LinearLayout.LayoutParams(0, dp(a, 44), 1f);
        lpN.setMargins(dp(a, 2), 0, dp(a, 2), 0);
        row.addView(yes, lpY);
        row.addView(no, lpN);
        box.addView(row, rowLp);

        final AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle(title)
                .setView(box)
                .setCancelable(true)
                .create();

        final Handler h = new Handler(Looper.getMainLooper());
        final int[] swapsDone = {0};
        final int[] attempts = {0};
        final int swapsMax = 3 + new Random().nextInt(2);
        final boolean[] yesLeft = {true};
        final Runnable[] step = new Runnable[1];
        step[0] = () -> {
            if (!dialog.isShowing() || attempts[0] > 8) return;
            attempts[0]++;
            if (yes.isPressed() || no.isPressed()) {
                h.postDelayed(step[0], 300);
                return;
            }
            if (swapsDone[0] >= swapsMax) return;
            row.removeAllViews();
            if (yesLeft[0]) {
                row.addView(no, lpN);
                row.addView(yes, lpY);
                yesLeft[0] = false;
            } else {
                row.addView(yes, lpY);
                row.addView(no, lpN);
                yesLeft[0] = true;
            }
            swapsDone[0]++;
            h.postDelayed(step[0], 800);
        };

        dialog.setOnDismissListener(d -> h.removeCallbacksAndMessages(null));
        yes.setOnClickListener(v -> {
            dialog.dismiss();
            onYes.run();
        });
        no.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        h.postDelayed(step[0], 800);
    }

    private static Button button(Activity a, String text, boolean primary) {
        Button b = new Button(a);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setSingleLine(true);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(0, 0, 0, 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(a, 10));
        if (primary) {
            bg.setColor(a.getColor(R.color.accent));
            b.setTextColor(ColorPicker.contrast(a.getColor(R.color.accent)));
            b.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            bg.setColor(a.getColor(R.color.button));
            bg.setStroke(dp(a, 1), a.getColor(R.color.stroke));
            b.setTextColor(a.getColor(R.color.text));
        }
        b.setBackground(bg);
        return b;
    }

    private static int dp(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }
}
