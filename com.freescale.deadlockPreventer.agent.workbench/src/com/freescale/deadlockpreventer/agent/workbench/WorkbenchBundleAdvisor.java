package com.freescale.deadlockpreventer.agent.workbench;

import java.util.ArrayList;

import com.freescale.deadlockpreventer.QueryService.IBuildInfoAdvisor;

public class WorkbenchBundleAdvisor implements IBuildInfoAdvisor {

	public WorkbenchBundleAdvisor() {}
	
	@Override
	public String getBundleInfo(ClassLoader classloader, Class cls,
			ArrayList<String> packages) {
		if (classloader.getClass().getName().equals("sun.misc.AppLoader")) {
			return "foo";
		}
		return classloader.toString();
	}
}
