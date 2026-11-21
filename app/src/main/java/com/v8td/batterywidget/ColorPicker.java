package com.v8td.batterywidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Редактор цвета (0.7): превью с HEX внутри, проверка читаемости на подложке,
 *  квадраты групп, стили и таблица 7×30 (210 оттенков на стиль, 5880 цветов всего). */
final class ColorPicker {

    interface Callback {
        void onColor(int color);
    }

    /** Порядок как в считалке: Каждый Охотник Желает Знать, Где Сидит Фазан. */
    private static final String[] GROUP_NAMES =
            {"Красный", "Оранжевый", "Жёлтый", "Зелёный", "Голубой", "Синий", "Фиолетовый"};
    private static final float[] GROUP_HUES = {0f, 28f, 55f, 120f, 190f, 225f, 275f};
    private static final String[] STYLE_NAMES = {"Стандарт", "Неон", "Пастель", "Карамель"};

    /** Таблица: 7 столбцов (насыщенность: слева бледнее, справа насыщеннее)
     *  и 30 строк (яркость: сверху темнее, снизу светлее). */
    private static final int COLS = 7;
    private static final int ROWS = 30;
    private static final float[] SAT_MIN = {0.30f, 0.82f, 0.18f, 0.72f};
    private static final float[] SAT_STEP = {0.115f, 0.03f, 0.045f, 0.04f};
    private static final float[] VAL_MIN = {0.16f, 0.20f, 0.58f, 0.26f};
    private static final float[] VAL_SPAN = {0.84f, 0.80f, 0.42f, 0.46f};

    /** Подложки проверки читаемости: чёрная, белая, цветная, пёстрое «фото». */
    private static final int[] BG_COLORS = {0xFF000000, 0xFFFFFFFF, 0xFF3A6EA5, 0};
    private static final String[] BG_DESC =
            {"Чёрный фон", "Белый фон", "Цветной фон", "Пёстрый фон (фото)"};

    private static final Pattern HEX_PATTERN = Pattern.compile("#?[0-9A-Fa-f]{6}");

    private ColorPicker() {
    }

    static int contrast(int color) {
        int luma = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000;
        return luma > 145 ? 0xFF10161F : 0xFFFFFFFF;
    }

    static String toHex(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    /** Принимает ТОЛЬКО полные шесть символов (RRGGBB или #RRGGBB).
     *  Короткие трёхзначные коды убраны: из-за них стирание символов само меняло цвет. */
    static Integer parseHex(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return null;
        try {
            return (int) (0xFF000000L | Long.parseLong(s, 16));
        } catch (Throwable t) {
            return null;
        }
    }

    /** Ищет HEX в произвольном тексте (для кнопки «Вставить»). */
    private static Integer extractHex(String raw) {
        if (raw == null) return null;
        Integer whole = parseHex(raw);
        if (whole != null) return whole;
        Matcher m = HEX_PATTERN.matcher(raw);
        return m.find() ? parseHex(m.group()) : null;
    }

    /** Цвет клетки таблицы: группа, стиль, столбец, строка (0 = самая тёмная). */
    private static int tableColor(int group, int style, int col, int row) {
        float sat = Math.min(1f, SAT_MIN[style] + SAT_STEP[style] * col);
        float val = Math.min(1f, VAL_MIN[style] + VAL_SPAN[style] * row / (ROWS - 1));
        return Color.HSVToColor(new float[]{GROUP_HUES[group], sat, val});
    }

    private static long dist2(int a, int b) {
        int dr = Color.red(a) - Color.red(b);
        int dg = Color.green(a) - Color.green(b);
        int db = Color.blue(a) - Color.blue(b);
        return (long) dr * dr + (long) dg * dg + (long) db * db;
    }

    /** Ближайшая клетка во всей таблице: {группа, стиль, столбец, строка, 1 = точное совпадение}. */
    private static int[] nearest(int color) {
        int bg = 0, bs = 0, bc = 0, br = 0;
        long best = Long.MAX_VALUE;
        for (int g = 0; g < GROUP_HUES.length; g++) {
            for (int s = 0; s < STYLE_NAMES.length; s++) {
                for (int c = 0; c < COLS; c++) {
                    for (int r = 0; r < ROWS; r++) {
                        long d = dist2(color, tableColor(g, s, c, r));
                        if (d < best) {
                            best = d;
                            bg = g;
                            bs = s;
                            bc = c;
                            br = r;
                        }
                    }
                }
            }
        }
        return new int[]{bg, bs, bc, br, best == 0 ? 1 : 0};
    }

    /** «Фото» для проверки читаемости: пёстрый фон рисуем кодом,
     *  чтобы не класть в репозиторий бинарную картинку. */
    private static Bitmap makePhoto() {
        int w = 240;
        int h = 160;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Random rnd = new Random(42);
        cv.drawColor(Color.HSVToColor(new float[]{205f, 0.30f, 0.32f}));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < 48; i++) {
            p.setColor(Color.HSVToColor(new float[]{
                    rnd.nextFloat() * 360f,
                    0.30f + rnd.nextFloat() * 0.55f,
                    0.25f + rnd.nextFloat() * 0.70f}));
            p.setAlpha(80 + rnd.nextInt(130));
            cv.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h,
                    8f + rnd.nextFloat() * 38f, p);
        }
        return bmp;
    }

    static void open(Activity a, String title, int initial, Callback callback) {
        final int[] cur = {initial};
        final int[] near0 = nearest(initial);
        final int[] vg = {near0[0]};
        final int[] vs = {near0[1]};
        final int[][] lastNear = {near0};
        final int[] bgIndex = {0};
        final boolean[] lockHex = {false};

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dpPx(a, 20), dpPx(a, 10), dpPx(a, 20), dpPx(a, 12));

        ScrollView scroll = new ScrollView(a);

        // --- 1. Надпись диапазона сверху, под ней плашка цвета с HEX внутри. ---
        TextView rangeLabel = new TextView(a);
        rangeLabel.setText("Диапазон " + title);
        rangeLabel.setTextSize(15);
        rangeLabel.setTextColor(a.getColor(R.color.text));
        rangeLabel.setPadding(dpPx(a, 2), dpPx(a, 4), dpPx(a, 2), dpPx(a, 6));

        LinearLayout previewBox = new LinearLayout(a);
        previewBox.setOrientation(LinearLayout.HORIZONTAL);
        previewBox.setGravity(Gravity.CENTER);

        EditText hex = new EditText(a);
        hex.setSingleLine(true);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
        hex.setTextSize(16);
        hex.setTypeface(Typeface.DEFAULT_BOLD);
        hex.setGravity(Gravity.CENTER);
        hex.setBackground(null);
        // Внутренние отступы = увеличенная зона тапа по всем сторонам.
        hex.setPadding(dpPx(a, 14), dpPx(a, 10), dpPx(a, 14), dpPx(a, 10));
        hex.setHint("#35D9FF");
        hex.setText(toHex(initial));
        previewBox.addView(hex, new LinearLayout.LayoutParams(-2, -2));

        Button copy = smallButton(a, "Копировать");
        Button paste = smallButton(a, "Вставить");
        LinearLayout buttonsRow = new LinearLayout(a);
        buttonsRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView hexStatus = new TextView(a);
        hexStatus.setTextSize(12);
        hexStatus.setTextColor(a.getColor(R.color.muted));
        hexStatus.setPadding(dpPx(a, 2), dpPx(a, 4), dpPx(a, 2), 0);

        // --- 2. Читаемость: плитка размером с виджет и четыре квадратные подложки. ---
        final TextView tile = new TextView(a);
        tile.setText("88");
        tile.setGravity(Gravity.CENTER);
        tile.setTypeface(Typeface.DEFAULT_BOLD);
        tile.setOutlineProvider(rounded(a, dpPx(a, 12)));
        tile.setClipToOutline(true);

        final Bitmap photo = makePhoto();
        final View[] bgSquares = new View[4];
        LinearLayout rightBox = new LinearLayout(a);
        rightBox.setOrientation(LinearLayout.VERTICAL);
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 2; c++) {
                int idx = r * 2 + c;
                View sq = new SquareView(a);
                sq.setContentDescription(BG_DESC[idx]);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -2, 1f);
                slp.setMargins(dpPx(a, 3), dpPx(a, 3), dpPx(a, 3), dpPx(a, 3));
                row.addView(sq, slp);
                bgSquares[idx] = sq;
            }
            rightBox.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }

        LinearLayout readRow = new LinearLayout(a);
        readRow.setOrientation(LinearLayout.HORIZONTAL);
        readRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(dpPx(a, 104), dpPx(a, 104));
        tileLp.setMargins(dpPx(a, 2), dpPx(a, 2), dpPx(a, 8), dpPx(a, 2));
        readRow.addView(tile, tileLp);
        readRow.addView(rightBox, new LinearLayout.LayoutParams(dpPx(a, 104), -2));

        // Цифры плитки подбираются измерением: «88» занимает 86% ширины,
        // те же пропорции, что на реальном виджете.
        tile.post(() -> {
            Paint mp = new Paint(tile.getPaint());
            mp.setTextSize(100f);
            float w = mp.measureText("88");
            if (w > 0f && tile.getWidth() > 0) {
                tile.setTextSize(TypedValue.COMPLEX_UNIT_PX, 100f * tile.getWidth() * 0.86f / w);
            }
        });

        // --- 3. Семь квадратных цветных кнопок групп. ---
        LinearLayout chipsRow = new LinearLayout(a);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        View[] chips = new View[GROUP_NAMES.length];
        for (int i = 0; i < chips.length; i++) {
            View chip = new SquareView(a);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
            clp.setMargins(dpPx(a, 2), dpPx(a, 2), dpPx(a, 2), dpPx(a, 2));
            chipsRow.addView(chip, clp);
            chips[i] = chip;
        }

        // Надпись над квадратами: показывает имя выбранной группы.
        TextView groupLabel = new TextView(a);
        groupLabel.setTextSize(13);
        groupLabel.setTextColor(a.getColor(R.color.muted));
        groupLabel.setPadding(0, dpPx(a, 14), 0, dpPx(a, 4));
        groupLabel.setText(GROUP_NAMES[vg[0]]);

        // --- 4. Кнопки стилей: одна строка, шрифт сам подбирается под ширину. ---
        LinearLayout stylesRow = new LinearLayout(a);
        stylesRow.setOrientation(LinearLayout.HORIZONTAL);
        Button[] styleBtns = new Button[STYLE_NAMES.length];
        for (int i = 0; i < styleBtns.length; i++) {
            Button b = new Button(a);
            b.setText(STYLE_NAMES[i]);
            b.setAllCaps(false);
            b.setSingleLine(true);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setPadding(dpPx(a, 6), 0, dpPx(a, 6), 0);
            b.setAutoSizeTextTypeUniformWithConfiguration(9, 13, 1, TypedValue.COMPLEX_UNIT_SP);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dpPx(a, 44), 1f);
            blp.setMargins(dpPx(a, 2), dpPx(a, 2), dpPx(a, 2), dpPx(a, 2));
            stylesRow.addView(b, blp);
            styleBtns[i] = b;
        }

        TextView tableCaption = new TextView(a);
        tableCaption.setText("Оттенки: сверху тёмные, снизу светлые;"
                + " слева бледные, справа насыщенные");
        tableCaption.setTextSize(11);
        tableCaption.setTextColor(a.getColor(R.color.muted));
        tableCaption.setPadding(dpPx(a, 2), dpPx(a, 8), dpPx(a, 2), dpPx(a, 4));

        // --- 5. Таблица 7×30 одним полотном. ---
        ShadeTable table = new ShadeTable(a);
        table.setView(vg[0], vs[0]);
        table.setMark(near0[2], near0[3], near0[4] == 1);

        // --- Перерисовка квадратов групп. ---
        final Runnable[] drawChips = new Runnable[1];
        drawChips[0] = () -> {
            for (int i = 0; i < chips.length; i++) {
                GradientDrawable shape = new GradientDrawable();
                shape.setColor(Color.HSVToColor(new float[]{GROUP_HUES[i], 0.88f, 0.92f}));
                shape.setCornerRadius(dpPx(a, 6));
                if (i == vg[0]) {
                    shape.setStroke(dpPx(a, 3), a.getColor(R.color.accent));
                }
                chips[i].setBackground(shape);
                chips[i].setContentDescription(GROUP_NAMES[i]);
            }
        };

        // --- Перерисовка кнопок стилей. ---
        final Runnable[] drawStyles = new Runnable[1];
        drawStyles[0] = () -> {
            for (int i = 0; i < styleBtns.length; i++) {
                boolean sel = i == vs[0];
                Button b = styleBtns[i];
                b.setTextColor(a.getColor(sel ? R.color.text : R.color.muted));
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(a.getColor(R.color.button));
                bg.setCornerRadius(dpPx(a, 10));
                bg.setStroke(dpPx(a, sel ? 2 : 1),
                        sel ? a.getColor(R.color.accent) : a.getColor(R.color.stroke));
                b.setBackground(bg);
            }
        };

        // --- Прокрутка к строке текущего цвета: сначала возвращаем таблицу
        //     к группе и стилю, где цвет живёт, потом едем к строке. ---
        final Runnable scrollToColor = () -> {
            int[] n = lastNear[0];
            if (vg[0] != n[0] || vs[0] != n[1]) {
                vg[0] = n[0];
                vs[0] = n[1];
                groupLabel.setText(GROUP_NAMES[vg[0]]);
                drawChips[0].run();
                drawStyles[0].run();
                table.setView(vg[0], vs[0]);
            }
            table.setMark(n[2], n[3], n[4] == 1);
            int y = table.getTop() + n[3] * table.rowHeight() - scroll.getHeight() / 2;
            scroll.smoothScrollTo(0, Math.max(0, y));
        };

        // --- Плитка-виджет и квадраты подложек. ---
        final Runnable drawReadability = () -> {
            tile.setBackground(bgIndex[0] == 3
                    ? new BitmapDrawable(a.getResources(), photo)
                    : new ColorDrawable(BG_COLORS[bgIndex[0]]));
            for (int i = 0; i < bgSquares.length; i++) {
                Drawable base = i == 3
                        ? new BitmapDrawable(a.getResources(), photo)
                        : new ColorDrawable(BG_COLORS[i]);
                GradientDrawable ringShape = new GradientDrawable();
                ringShape.setCornerRadius(dpPx(a, 8));
                ringShape.setStroke(dpPx(a, i == bgIndex[0] ? 2 : 1),
                        i == bgIndex[0] ? a.getColor(R.color.accent) : a.getColor(R.color.stroke));
                bgSquares[i].setBackground(
                        new LayerDrawable(new Drawable[]{base, ringShape}));
                bgSquares[i].setOutlineProvider(rounded(a, dpPx(a, 8)));
                bgSquares[i].setClipToOutline(true);
            }
        };

        // --- Кольцо: видно, только если ближайший цвет в открытой сейчас паре. ---
        final Runnable refreshMark = () -> {
            int[] n = lastNear[0];
            if (n[0] == vg[0] && n[1] == vs[0]) {
                table.setMark(n[2], n[3], n[4] == 1);
            } else {
                table.clearMark();
            }
        };

        // --- Главное обновление: плашка, HEX, статус, квадраты, стили, кольцо, плитка. ---
        final Runnable apply = () -> {
            int color = cur[0];
            GradientDrawable pbg = new GradientDrawable();
            pbg.setColor(color);
            pbg.setCornerRadius(dpPx(a, 12));
            previewBox.setBackground(pbg);
            hex.setTextColor(contrast(color));
            if (!lockHex[0]) {
                lockHex[0] = true;
                hex.setText(toHex(color));
                hex.setSelection(hex.getText().length());
                lockHex[0] = false;
            }
            int[] near = nearest(color);
            lastNear[0] = near;
            hexStatus.setText(near[4] == 1
                    ? "Этот цвет есть в таблице: «" + GROUP_NAMES[near[0]]
                    + "», стиль «" + STYLE_NAMES[near[1]] + "»"
                    : "Свой цвет; ближайший в таблице: «" + GROUP_NAMES[near[0]]
                    + "», стиль «" + STYLE_NAMES[near[1]] + "»");
            if (vg[0] != near[0] || vs[0] != near[1]) {
                vg[0] = near[0];
                vs[0] = near[1];
                groupLabel.setText(GROUP_NAMES[vg[0]]);
                drawChips[0].run();
                drawStyles[0].run();
                table.setView(vg[0], vs[0]);
            }
            table.setMark(near[2], near[3], near[4] == 1);
            tile.setTextColor(color);
        };

        // --- Привязка событий. ---
        previewBox.setOnClickListener(v -> scrollToColor.run());

        hex.setOnClickListener(v -> {
            hex.requestFocus();
            InputMethodManager imm =
                    (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(hex, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        for (int i = 0; i < chips.length; i++) {
            final int gi = i;
            chips[i].setOnClickListener(v -> {
                vg[0] = gi;
                groupLabel.setText(GROUP_NAMES[gi]);
                drawChips[0].run();
                table.setView(vg[0], vs[0]);
                refreshMark.run();
            });
        }
        for (int i = 0; i < styleBtns.length; i++) {
            final int si = i;
            styleBtns[i].setOnClickListener(v -> {
                vs[0] = si;
                drawStyles[0].run();
                table.setView(vg[0], vs[0]);
                refreshMark.run();
            });
        }
        for (int i = 0; i < bgSquares.length; i++) {
            final int bi = i;
            bgSquares[i].setOnClickListener(v -> {
                bgIndex[0] = bi;
                drawReadability.run();
            });
        }

        table.setOnCellTap(color -> {
            cur[0] = color;
            apply.run();
        });

        hex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (lockHex[0]) return;
                Integer parsed = parseHex(s.toString());
                if (parsed == null) return;
                cur[0] = parsed;
                apply.run();
            }
        });

        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("color", hex.getText().toString()));
                Toast.makeText(a, "Скопировано", Toast.LENGTH_SHORT).show();
            }
        });

        paste.setOnClickListener(v -> {
            CharSequence s = null;
            try {
                ClipboardManager cm =
                        (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip()) {
                    s = cm.getPrimaryClip().getItemAt(0).coerceToText(a);
                }
            } catch (Throwable ignored) {
            }
            Integer parsed = extractHex(s == null ? null : s.toString());
            if (parsed == null) {
                Toast.makeText(a, "В буфере не найден HEX-цвет", Toast.LENGTH_SHORT).show();
            } else {
                cur[0] = parsed;
                apply.run();
            }
        });

        // --- Сборка страницы. ---
        box.addView(rangeLabel, new LinearLayout.LayoutParams(-1, -2));
        box.addView(previewBox, new LinearLayout.LayoutParams(-1, dpPx(a, 72)));
        box.addView(hint(a, "Нажатие на цвет — прыжок к цвету в таблице;"
                + " нажатие на код — правка вручную."));
        buttonsRow.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        buttonsRow.addView(paste, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams brlp = new LinearLayout.LayoutParams(-1, -2);
        brlp.topMargin = dpPx(a, 4);
        box.addView(buttonsRow, brlp);
        box.addView(hexStatus, new LinearLayout.LayoutParams(-1, -2));
        box.addView(label(a, "Читаемость цифр на обоях"));
        box.addView(readRow, new LinearLayout.LayoutParams(-1, -2));
        box.addView(hint(a, "Квадраты справа — подложка под виджет: чёрная, белая,"
                + " цветная и пёстрая «фото»."));
        box.addView(groupLabel, new LinearLayout.LayoutParams(-1, -2));
        box.addView(chipsRow, new LinearLayout.LayoutParams(-1, -2));
        box.addView(label(a, "Стиль"));
        box.addView(stylesRow, new LinearLayout.LayoutParams(-1, -2));
        box.addView(tableCaption, new LinearLayout.LayoutParams(-1, -2));
        box.addView(table, new LinearLayout.LayoutParams(-1, -2));
        box.addView(hint(a, "Нажми клетку — выбрать цвет. Кольцом отмечен выбранный"
                + " (или ближайший к своему) цвет."));

        drawChips[0].run();
        drawStyles[0].run();
        drawReadability.run();
        apply.run();

        scroll.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle("Цвет")
                .setView(scroll)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (d, w) -> callback.onColor(cur[0]))
                .create();

        // Окно шире системного умолчания: почти вся ширина экрана.
        // После расширения — автопрокрутка к строке выбранного цвета.
        dialog.setOnShowListener(d -> {
            Window win = dialog.getWindow();
            if (win != null) {
                int screenW = a.getResources().getDisplayMetrics().widthPixels;
                win.setLayout(screenW - dpPx(a, 24), WindowManager.LayoutParams.WRAP_CONTENT);
            }
            scroll.post(() -> scroll.post(() -> {
                int[] n = lastNear[0];
                int y = table.getTop() + n[3] * table.rowHeight() - scroll.getHeight() / 2;
                scroll.scrollTo(0, Math.max(0, y));
            }));
        });
        dialog.show();
    }

    private static Button smallButton(Activity a, String text) {
        Button b = new Button(a);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dpPx(a, 10), 0, dpPx(a, 10), 0);
        b.setTextColor(a.getColor(R.color.text));
        return b;
    }

    private static TextView label(Activity a, String text) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(a.getColor(R.color.muted));
        t.setPadding(0, dpPx(a, 14), 0, dpPx(a, 4));
        return t;
    }

    private static TextView hint(Activity a, String text) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(11);
        t.setTextColor(a.getColor(R.color.muted));
        t.setPadding(dpPx(a, 2), dpPx(a, 4), dpPx(a, 2), 0);
        return t;
    }

    private static ViewOutlineProvider rounded(Activity a, float radiusPx) {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        };
    }

    private static int dpPx(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    /** Квадратная кнопка: высота всегда равна ширине, как на эталонном скриншоте. */
    private static final class SquareView extends View {
        SquareView(Context c) {
            super(c);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int w = MeasureSpec.getSize(widthSpec);
            setMeasuredDimension(w, w);
        }
    }

    /** Вся таблица (7 столбцов × 30 строк) рисуется одним View, а не сотней клеток. */
    private static final class ShadeTable extends View {

        interface OnCellTap {
            void onTap(int color);
        }

        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int cellH;
        private int viewGroup = 0;
        private int viewStyle = 0;
        private int markCol = -1;
        private int markRow = -1;
        private boolean markExact = false;
        private OnCellTap listener;
        private float lastX = -1;
        private float lastY = -1;

        ShadeTable(Context c) {
            super(c);
            cellH = dpPx(c, 30);
            ring.setStyle(Paint.Style.STROKE);
        }

        int rowHeight() {
            return cellH;
        }

        void setView(int group, int style) {
            if (viewGroup != group || viewStyle != style) {
                viewGroup = group;
                viewStyle = style;
                invalidate();
            }
        }

        void setMark(int col, int row, boolean exact) {
            markCol = col;
            markRow = row;
            markExact = exact;
            invalidate();
        }

        void clearMark() {
            markCol = -1;
            markRow = -1;
            invalidate();
        }

        void setOnCellTap(OnCellTap l) {
            listener = l;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), ROWS * cellH);
        }

        @Override
        protected void onDraw(Canvas cv) {
            Context c = getContext();
            float gap = dpPx(c, 2);
            float radius = dpPx(c, 5);
            float cw = getWidth() / (float) COLS;
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    fill.setColor(tableColor(viewGroup, viewStyle, col, row));
                    rect.set(col * cw + gap, row * cellH + gap,
                            (col + 1) * cw - gap, (row + 1) * cellH - gap);
                    cv.drawRoundRect(rect, radius, radius, fill);
                }
            }
            if (markCol >= 0 && markRow >= 0) {
                ring.setColor(c.getColor(R.color.accent));
                float sw = dpPx(c, markExact ? 3 : 2);
                ring.setStrokeWidth(sw);
                rect.set(markCol * cw + gap, markRow * cellH + gap,
                        (markCol + 1) * cw - gap, (markRow + 1) * cellH - gap);
                rect.inset(sw / 2f, sw / 2f);
                cv.drawRoundRect(rect, radius, radius, ring);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            lastX = e.getX();
            lastY = e.getY();
            if (e.getAction() == MotionEvent.ACTION_UP) {
                performClick();
            }
            return true;
        }

        @Override
        public boolean performClick() {
            if (listener != null && lastX >= 0 && getWidth() > 0) {
                int col = (int) (lastX / (getWidth() / (float) COLS));
                int row = (int) (lastY / cellH);
                if (col >= 0 && col < COLS && row >= 0 && row < ROWS) {
                    listener.onTap(tableColor(viewGroup, viewStyle, col, row));
                }
            }
            return super.performClick();
        }
    }
}
