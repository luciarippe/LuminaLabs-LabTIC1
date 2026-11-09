package uy.edu.um.luminalabs.dto;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// Una opcion de las listas desplegables de tiempo. Todo lo que tiene que ver con horarios se elige en horas,
// de a 30 minutos: la duracion, hasta cuando se puede reservar, el plazo de cancelacion y la hora de inicio.
// "value" es lo que se envia en el formulario (minutos, o "HH:mm" para la hora de inicio) y "label" lo que se ve.
public record HalfHourOption(String value, String label) {

    public static final int STEP_MINUTES = 30;

    // Duraciones: de 30 minutos a 24 horas. "current" es el valor guardado: si no esta en la lista (datos
    // anteriores a este cambio) se agrega, para no cambiarlo sin que el prestador se de cuenta.
    public static List<HalfHourOption> durations(int minMinutes, int maxMinutes, Integer current) {
        List<HalfHourOption> options = new ArrayList<>();
        for (int minutes = minMinutes; minutes <= maxMinutes; minutes += STEP_MINUTES) {
            options.add(new HalfHourOption(String.valueOf(minutes), hoursLabel(minutes)));
        }
        if (current != null && (current % STEP_MINUTES != 0 || current < minMinutes || current > maxMinutes)) {
            options.add(new HalfHourOption(String.valueOf(current), hoursLabel(current)));
        }
        return options;
    }

    // Horas de inicio del dia: 00:00, 00:30, ... 23:30 (y la guardada si no cae en media hora)
    public static List<HalfHourOption> timesOfDay(LocalTime current) {
        List<HalfHourOption> options = new ArrayList<>();
        for (LocalTime time = LocalTime.MIDNIGHT; ; time = time.plusMinutes(STEP_MINUTES)) {
            options.add(new HalfHourOption(time.toString(), time + " h"));
            if (time.equals(LocalTime.of(23, 30))) {
                break;
            }
        }
        if (current != null && !isOnHalfHour(current)) {
            LocalTime truncated = current.withSecond(0).withNano(0);
            options.add(new HalfHourOption(truncated.toString(), truncated + " h"));
        }
        return options;
    }

    public static boolean isOnHalfHour(LocalTime time) {
        return time.getMinute() % STEP_MINUTES == 0 && time.getSecond() == 0 && time.getNano() == 0;
    }

    // Minutos expresados en horas: 30 -> "0,5 h", 60 -> "1 h", 90 -> "1,5 h", 1440 -> "24 h"
    public static String hoursLabel(int minutes) {
        int hours = minutes / 60;
        int rest = minutes % 60;
        if (rest == 0) {
            return hours + " h";
        }
        if (rest == 30) {
            return hours + ",5 h";
        }
        // Valores viejos que no caen en media hora (ej. 45 minutos): se redondea a dos decimales
        String decimal = String.format(java.util.Locale.ROOT, "%.2f", minutes / 60.0).replaceAll("0+$", "");
        return decimal.replace('.', ',') + " h";
    }
}
