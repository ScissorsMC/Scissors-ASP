package io.github.scissorsmc;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeTags("VanillaFeature")
@SelectClasses(FlatWorldBaselineTest.class)
@ConfigurationParameter(key = "TestSuite", value = "VanillaFeature")
public class FlatWorldTestSuite {
}
