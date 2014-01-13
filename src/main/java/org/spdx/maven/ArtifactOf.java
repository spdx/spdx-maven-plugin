/*
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spdx.maven;

import java.net.URL;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Class to contain information on an SPDX doap project using 
 * maven parameters to describe the package
 * @author Gary O'Neall
 *
 */
public class ArtifactOf {

	/**
	 * Required name of a project from which a file was derived
	 */
	@Parameter(required = true)
	String name;
	
	/**
	 * Optional parameter to indicate the location of the project from which the file has been derived
	 */
	@Parameter
	URL homePage;

	public ArtifactOf() {
		
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return the homePage
	 */
	public URL getHomePage() {
		return homePage;
	}

}
