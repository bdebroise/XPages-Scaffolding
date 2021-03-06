package frostillicus.xsp.controller;


import javax.faces.application.ViewHandler;
import javax.faces.component.UIViewRoot;
import javax.faces.context.FacesContext;
import javax.faces.el.MethodBinding;
import javax.faces.event.PhaseEvent;

import frostillicus.xsp.util.FrameworkUtils;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.component.UIViewRootEx;

import org.openntf.domino.*;

import java.util.*;

public class ControllingViewHandler extends com.ibm.xsp.application.ViewHandlerExImpl {
	public static final String BEAN_NAME = "controller";

	public ControllingViewHandler(final ViewHandler delegate) {
		super(delegate);
	}

	@SuppressWarnings("unchecked")
	@Override
	public UIViewRoot createView(final FacesContext context, final String pageName) {
		// Page name is in the format "/home"
		String pageClassName = pageName.substring(1);

		if(pageClassName.equalsIgnoreCase("$$OpenDominoDocument")) {
			// Handle the virtual document page. There may be a better way to do this, but this will do for now
			Database database = (Database)resolveVariable(context, "database");
			Map<String, String> param = (Map<String, String>)resolveVariable(context, "param");
			Document doc = database.getDocumentByUNID(param.get("documentId"));
			if(doc != null) {
				String formName = doc.getItemValueString("Form");

				// Now that we have the form, look for the XPageAlt
				Form form = database.getForm(formName);
				String formURL = form.getURL();
				String formUNID = FrameworkUtils.strRightBack(FrameworkUtils.strLeftBack(formURL, "?Open"), "/");
				Document formDoc = database.getDocumentByUNID(formUNID);
				String xpageAlt = formDoc.getItemValueString("$XPageAlt");

				if(StringUtil.isEmpty(xpageAlt)) {
					pageClassName = formName;
				} else {
					pageClassName = FrameworkUtils.strLeftBack(xpageAlt, ".xsp");
				}
			}
		}

		Class<? extends XPageController> controllerClass = null;
		try {
			controllerClass = (Class<? extends XPageController>)context.getContextClassLoader().loadClass("controller." + pageClassName);
		} catch(ClassNotFoundException cnfe) {
			controllerClass = BasicXPageController.class;
		}
		UIViewRootEx root = null;
		try {
			XPageController pageController = controllerClass.newInstance();
			Map<String, Object> requestScope = (Map<String, Object>)resolveVariable(context, "requestScope");
			requestScope.put(BEAN_NAME, pageController);

			root = (UIViewRootEx)super.createView(context, pageName);
			root.getViewMap().put(BEAN_NAME, pageController);
			requestScope.remove(BEAN_NAME);

			MethodBinding beforeRenderResponse = context.getApplication().createMethodBinding("#{" + BEAN_NAME + ".beforeRenderResponse}", new Class[] { PhaseEvent.class });
			root.setBeforeRenderResponse(beforeRenderResponse);

			MethodBinding afterRenderResponse = context.getApplication().createMethodBinding("#{" + BEAN_NAME + ".afterRenderResponse}", new Class[] { PhaseEvent.class });
			root.setAfterRenderResponse(afterRenderResponse);

			MethodBinding afterRestoreView = context.getApplication().createMethodBinding("#{" + BEAN_NAME + ".afterRestoreView}", new Class[] { PhaseEvent.class });
			root.setAfterRestoreView(afterRestoreView);
		} catch(Exception e) {
			e.printStackTrace();
			root = (UIViewRootEx)super.createView(context, pageName);
		}

		return root;
	}

	private static Object resolveVariable(final FacesContext context, final String varName) {
		return context.getApplication().getVariableResolver().resolveVariable(context, varName);
	}
}