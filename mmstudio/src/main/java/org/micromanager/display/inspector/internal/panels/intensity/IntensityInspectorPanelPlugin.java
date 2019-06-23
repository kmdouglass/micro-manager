
package org.micromanager.display.inspector.internal.panels.intensity;

import org.micromanager.Studio;
import org.micromanager.display.DataViewer;
import org.scijava.Priority;
import org.scijava.plugin.Plugin;
import org.micromanager.display.inspector.InspectorPanelController;
import org.micromanager.display.inspector.InspectorPanelPlugin;

/**
 *
 * @author mark
 */
@Plugin(type = InspectorPanelPlugin.class,
      priority = Priority.VERY_HIGH_PRIORITY,
      name = "Intensity Scaling",
      description = "View and adjust intensity scaling")
public class IntensityInspectorPanelPlugin implements InspectorPanelPlugin {
   @Override
   public boolean isApplicableToDataViewer(DataViewer viewer) {
      return viewer.getDataProvider() != null &&
            viewer instanceof ImageStatsPublisher;
   }

   @Override
   public InspectorPanelController createPanelController(Studio studio) {
      return IntensityInspectorPanelController.create(studio);
   }
}