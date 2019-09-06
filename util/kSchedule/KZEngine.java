package com.caishi.util;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * @author by keray
 * date:2019/9/5 15:08
 * kz表达式工具
 */
public class KZEngine {
    /**
     * <p>
     * <h3>作者 keray</h3>
     * <h3>时间： 2019/9/5 15:08</h3>
     * 校验kz表达式
     * </p>
     *
     * @param expression
     * @return <p> {@link boolean} </p>
     * @throws
     */
    public static boolean checkKZ(String expression) {
        if (expression == null || "".equals(expression)) {
            return false;
        }
        String[] es = es(expression);
        for (String e : es) {
            if (!e.matches("[+|\\-|]?[y|M|d|H|m|s|S]{1}[\\d]+")) {
                return false;
            }
        }
        return true;
    }

    /**
     * <p>
     * <h3>作者 keray</h3>
     * <h3>时间： 2019/9/5 15:26</h3>
     * 根据kz表达式和开始时间计算结束时间
     * </p>
     *
     * @param expression
     * @param date
     * @return <p> {@link LocalDateTime} </p>
     * @throws
     */
    public static LocalDateTime computeTime(String expression, LocalDateTime date) {
        if (!checkKZ(expression)) {
            throw new IllegalStateException("kz表达式不合法");
        }
        String[] es = es(expression);
        for (String e : es) {
            date = computeA(e, date);
        }
        return date;
    }

    private static String[] es(String expression) {
        String[] esA = expression.split(":");
        String[] esB = expression.split(" ");
        if (esA.length > esB.length || esA.length == esB.length) {
            return esA;
        }
        return esB;
    }

    /**
     * @param aEs
     * @param date
     */
    private static LocalDateTime computeA(String aEs, LocalDateTime date) {
        // 计算模式 false 减法 true 加法
        boolean state = !aEs.startsWith("-");
        aEs = aEs.substring(aEs.startsWith("-") || aEs.startsWith("+") ? 1 : 0);
        Integer value = Integer.valueOf(aEs.substring(1));
        switch (aEs.charAt(0)) {
            case 'y': {
                return date.minusYears(state ? -value : value);
            }
            case 'M': {
                return date.minusMonths(state ? -value : value);
            }
            case 'd': {
                return date.minusDays(state ? -value : value);
            }
            case 'H': {
                return date.minusHours(state ? -value : value);
            }
            case 'm': {
                return date.minusMinutes(state ? -value : value);
            }
            case 's': {
                return date.minusSeconds(state ? -value : value);
            }
            case 'S': {
                return date.minus(state ? -value : value, ChronoUnit.MILLENNIA);
            }
            default:
                return date;
        }
    }

    public static void main(String[] args) {
        System.out.println(computeTime("+y1 -M2", LocalDateTime.now()));
    }
}
