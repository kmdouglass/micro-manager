/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.ui.asigui;

import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

public class Spinner extends JSpinner {

   private static int defaultSize_ = 5;

   private Spinner(final Integer start, final Integer min, final Integer max, final Integer step) {
      super(new SpinnerNumberModel(start, min, max, step));
      setColumnSize(defaultSize_);
   }

   private Spinner(final Double start, final Double min, final Double max, final Double step) {
      super(new SpinnerNumberModel(start, min, max, step));
      setColumnSize(defaultSize_);
   }

   public static Spinner createIntegerSpinner(
         final Integer start,
         final Integer min,
         final Integer max,
         final Integer step) {
      return new Spinner(Math.max(min, Math.min(start, max)), min, max, step);
   }

   public static Spinner createDoubleSpinner(
         final Double start,
         final Double min,
         final Double max,
         final Double step) {
      return new Spinner(Math.max(min, Math.min(start, max)), min, max, step);
   }

   public void setColumnSize(final int width) {
      final JComponent editor = getEditor();
      final JFormattedTextField textField = ((NumberEditor) editor).getTextField();
      textField.setColumns(width);
   }

   public static void setDefaultSize(final int width) {
      defaultSize_ = width;
   }

   public int getInt() {
      return (Integer) getValue();
   }

   public double getDouble() {
      return (Double) getValue();
   }

   public void setInt(final int n) {
      setValue(n);
   }

   public void setDouble(final double n) {
      setValue(n);
   }

   public void registerListener(final Method method) {
      addChangeListener(method::run);
   }
}
