/*
 * Copyright © 2015 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.config;

import co.cask.cdap.common.BadRequestException;
import co.cask.cdap.common.NotFoundException;
import co.cask.cdap.common.ProfileConflictException;
import co.cask.cdap.internal.app.runtime.SystemArguments;
import co.cask.cdap.internal.app.services.http.AppFabricTestBase;
import co.cask.cdap.internal.profile.ProfileService;
import co.cask.cdap.proto.ProgramType;
import co.cask.cdap.proto.id.ApplicationId;
import co.cask.cdap.proto.id.EntityId;
import co.cask.cdap.proto.id.InstanceId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.ProfileId;
import co.cask.cdap.proto.id.ProgramId;
import co.cask.cdap.proto.profile.Profile;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests for {@link ConfigStore}, {@link ConsoleSettingsStore}, {@link DashboardStore} and {@link PreferencesService}.
 */
public class UserConfigStoreTest extends AppFabricTestBase {

  // Config Store tests
  @Test
  public void testSimpleConfig() throws Exception {
    String namespace = "myspace";
    String type = "dash";
    ConfigStore configStore = getInjector().getInstance(ConfigStore.class);
    Config myConfig = new Config("abcd", ImmutableMap.<String, String>of());
    configStore.create(namespace, type, myConfig);
    Assert.assertEquals(myConfig, configStore.get(namespace, type, myConfig.getId()));
    List<Config> configList = configStore.list(namespace, type);
    Assert.assertEquals(1, configList.size());
    Assert.assertEquals(myConfig, configList.get(0));

    Map<String, String> properties = Maps.newHashMap();
    properties.put("hello", "world");
    properties.put("config", "store");
    myConfig = new Config("abcd", properties);

    configStore.update(namespace, type, myConfig);
    Assert.assertEquals(myConfig, configStore.get(namespace, type, myConfig.getId()));
    configStore.delete(namespace, type, myConfig.getId());
    configList = configStore.list(namespace, type);
    Assert.assertEquals(0, configList.size());
  }

  @Test
  public void testMultipleConfigs() throws Exception {
    String ns1 = "space1";
    String ns2 = "space2";
    String type = "type";
    ConfigStore configStore = getInjector().getInstance(ConfigStore.class);
    Config config1 = new Config("config1", ImmutableMap.<String, String>of());
    Config config2 = new Config("config2", ImmutableMap.<String, String>of());
    Config config3 = new Config("config3", ImmutableMap.<String, String>of());
    configStore.create(ns1, type, config1);
    configStore.create(ns1, type, config2);
    configStore.create(ns2, type, config3);
    Assert.assertEquals(2, configStore.list(ns1, type).size());
    Assert.assertTrue(configStore.list(ns1, type).contains(config1));
    Assert.assertTrue(configStore.list(ns1, type).contains(config2));
    Assert.assertEquals(1, configStore.list(ns2, type).size());
    Assert.assertTrue(configStore.list(ns2, type).contains(config3));
    configStore.delete(ns1, type, config1.getId());
    configStore.delete(ns1, type, config2.getId());
    Assert.assertEquals(0, configStore.list(ns1, type).size());
    Assert.assertEquals(1, configStore.list(ns2, type).size());
    Assert.assertTrue(configStore.list(ns2, type).contains(config3));
    configStore.delete(ns2, type, config3.getId());
    Assert.assertEquals(0, configStore.list(ns2, type).size());
  }

  @Test(expected = ConfigExistsException.class)
  public void testDuplicateConfig() throws Exception {
    String namespace = "space";
    String type = "user";
    ConfigStore configStore = getInjector().getInstance(ConfigStore.class);
    Config myConfig = new Config("abcd", ImmutableMap.<String, String>of());
    configStore.create(namespace, type, myConfig);
    Assert.assertEquals(1, configStore.list(namespace, type).size());
    configStore.create(namespace, type, myConfig);
  }

  @Test
  public void testDuplicateConfigUpdate() throws Exception {
    String namespace = "oldspace";
    String type = "user";
    ConfigStore configStore = getInjector().getInstance(ConfigStore.class);
    Config myConfig = new Config("abcd", ImmutableMap.<String, String>of());
    configStore.create(namespace, type, myConfig);
    Assert.assertEquals(1, configStore.list(namespace, type).size());
    configStore.createOrUpdate(namespace, type, myConfig);
  }

  @Test(expected = ConfigNotFoundException.class)
  public void testDeleteUnknownConfig() throws Exception {
    String namespace = "newspace";
    String type = "prefs";
    ConfigStore configStore = getInjector().getInstance(ConfigStore.class);
    configStore.delete(namespace, type, "someid");
  }

  @Test(expected = ConfigNotFoundException.class)
  public void testGetUnknownConfig() throws Exception {
    String namespace = "newspace";
    String type = "prefs";
    ConfigStore configStore = getInjector().getInstance(ConfigStore.class);
    configStore.get(namespace, type, "someid");
  }

  @Test(expected = ConfigNotFoundException.class)
  public void testUpdateUnknownConfig() throws Exception {
    String namespace = "newspace";
    String type = "prefs";
    ConfigStore configStore = getInjector().getInstance(ConfigStore.class);
    Config myConfig = new Config("abcd", ImmutableMap.<String, String>of());
    configStore.update(namespace, type, myConfig);
  }

  @Test
  public void testRandomData() throws Exception {
    String namespace = "somesp@#ace123!@";
    String type = "s231!@#";
    String id = "kj324";
    ConfigStore configStore = getInjector().getInstance(ConfigStore.class);
    Map<String, String> prop = Maps.newHashMap();
    prop.put("j342", "9834@#($");
    prop.put("123jsd123@#", "????213");
    Config myConfig = new Config(id, prop);
    configStore.create(namespace, type, myConfig);
    Assert.assertEquals(1, configStore.list(namespace, type).size());
    Assert.assertEquals(myConfig, configStore.get(namespace, type, id));
    configStore.delete(namespace, type, id);
    Assert.assertEquals(0, configStore.list(namespace, type).size());
  }

  // Testing ConsoleSettingStore
  @Test
  public void testConsoleStore() throws Exception {
    ConsoleSettingsStore store = getInjector().getInstance(ConsoleSettingsStore.class);
    int configCount = 10;
    Map<String, String> emptyMap = ImmutableMap.of();
    for (int i = 0; i < configCount; i++) {
      store.put(new Config(String.valueOf(i), emptyMap));
    }

    Assert.assertEquals(configCount, store.list().size());
    store.delete();
    Assert.assertTrue(store.list().isEmpty());
  }

  // Testing DashboardStore
  @Test
  public void testDashboardDeletingNamespace() throws Exception {
    String namespace = "myspace";
    int dashboardCount = 10;
    Map<String, String> emptyMap = ImmutableMap.of();
    DashboardStore store = getInjector().getInstance(DashboardStore.class);
    for (int i = 0; i < dashboardCount; i++) {
      store.create(namespace, emptyMap);
    }

    Assert.assertEquals(dashboardCount, store.list(namespace).size());
    store.delete(namespace);
    Assert.assertTrue(store.list(namespace).isEmpty());
  }

  // Testing PrefernecesStore
  @Test
  public void testCleanSlate() throws Exception {
    Map<String, String> emptyMap = ImmutableMap.of();
    PreferencesService store = getInjector().getInstance(PreferencesService.class);
    Assert.assertEquals(emptyMap, store.getProperties());
    Assert.assertEquals(emptyMap, store.getProperties(new NamespaceId("somenamespace")));
    Assert.assertEquals(emptyMap, store.getProperties(NamespaceId.DEFAULT));
    Assert.assertEquals(emptyMap, store.getResolvedProperties());
    Assert.assertEquals(emptyMap, store.getResolvedProperties(new ProgramId("a", "b", ProgramType.WORKFLOW, "d")));
    // should not throw any exception if try to delete properties without storing anything
    store.deleteProperties();
    store.deleteProperties(NamespaceId.DEFAULT);
    store.deleteProperties(new ProgramId("a", "x", ProgramType.WORKFLOW, "z"));
  }

  @Test
  public void testBasicProperties() throws Exception {
    Map<String, String> propMap = Maps.newHashMap();
    propMap.put("key", "instance");
    PreferencesService store = getInjector().getInstance(PreferencesService.class);
    store.setProperties(propMap);
    Assert.assertEquals(propMap, store.getProperties());
    Assert.assertEquals(propMap, store.getResolvedProperties(new ProgramId("a", "b", ProgramType.WORKFLOW, "d")));
    Assert.assertEquals(propMap, store.getResolvedProperties(new NamespaceId("myspace")));
    Assert.assertEquals(ImmutableMap.<String, String>of(), store.getProperties(new NamespaceId("myspace")));
    store.deleteProperties();
    propMap.clear();
    Assert.assertEquals(propMap, store.getProperties());
    Assert.assertEquals(propMap, store.getResolvedProperties(new ProgramId("a", "b", ProgramType.WORKFLOW, "d")));
    Assert.assertEquals(propMap, store.getResolvedProperties(new NamespaceId("myspace")));
  }

  @Test
  public void testMultiLevelProperties() throws Exception {
    Map<String, String> propMap = Maps.newHashMap();
    propMap.put("key", "namespace");
    PreferencesService store = getInjector().getInstance(PreferencesService.class);
    store.setProperties(new NamespaceId("myspace"), propMap);
    propMap.put("key", "application");
    store.setProperties(new ApplicationId("myspace", "app"), propMap);
    Assert.assertEquals(propMap, store.getProperties(new ApplicationId("myspace", "app")));
    Assert.assertEquals("namespace", store.getProperties(new NamespaceId("myspace")).get("key"));
    Assert.assertTrue(store.getProperties(new ApplicationId("myspace", "notmyapp")).isEmpty());
    Assert.assertEquals("namespace", store.getResolvedProperties(new ApplicationId("myspace", "notmyapp")).get("key"));
    Assert.assertTrue(store.getProperties(new NamespaceId("notmyspace")).isEmpty());
    store.deleteProperties(new NamespaceId("myspace"));
    Assert.assertTrue(store.getProperties(new NamespaceId("myspace")).isEmpty());
    Assert.assertTrue(store.getResolvedProperties(new ApplicationId("myspace", "notmyapp")).isEmpty());
    Assert.assertEquals(propMap, store.getProperties(new ApplicationId("myspace", "app")));
    store.deleteProperties(new ApplicationId("myspace", "app"));
    Assert.assertTrue(store.getProperties(new ApplicationId("myspace", "app")).isEmpty());
    propMap.put("key", "program");
    store.setProperties(new ProgramId("myspace", "app", ProgramType.WORKFLOW, "prog"), propMap);
    Assert.assertEquals(propMap, store.getProperties(new ProgramId("myspace", "app", ProgramType.WORKFLOW, "prog")));
    store.setProperties(ImmutableMap.of("key", "instance"));
    Assert.assertEquals(propMap, store.getProperties(new ProgramId("myspace", "app", ProgramType.WORKFLOW, "prog")));
    store.deleteProperties(new ProgramId("myspace", "app", ProgramType.WORKFLOW, "prog"));
    Assert.assertTrue(store.getProperties(new ProgramId("myspace", "app", ProgramType.WORKFLOW, "prog")).isEmpty());
    Assert.assertEquals("instance", store.getResolvedProperties(
      new ProgramId("myspace", "app", ProgramType.WORKFLOW, "prog")).get("key"));
    store.deleteProperties();
    Assert.assertEquals(ImmutableMap.<String, String>of(), store.getProperties(
      new ProgramId("myspace", "app", ProgramType.WORKFLOW, "prog")));
  }

  @Test
  public void testAddProfileInProperties() throws Exception {
    PreferencesService prefStore = getInjector().getInstance(PreferencesService.class);
    ProfileService profileStore = getInjector().getInstance(ProfileService.class);

    // put a profile unrelated property should not affect the write
    Map<String, String> expected = new HashMap<>();
    expected.put("unRelatedKey", "unRelatedValue");
    prefStore.setProperties(new ProgramId("myspace", "app", ProgramType.WORKFLOW, "prog"), expected);
    Assert.assertEquals(expected, prefStore.getProperties(
      new ProgramId("myspace", "app", ProgramType.WORKFLOW, "prog")));

    // put something related to profile
    Map<String, String> profileMap = new HashMap<>();
    profileMap.put(SystemArguments.PROFILE_NAME, "userProfile");

    // this set call should fail since the profile does not exist
    try {
      prefStore.setProperties(new ProgramId("myspace", "app", ProgramType.WORKFLOW, "prog"), profileMap);
      Assert.fail();
    } catch (NotFoundException e) {
      // expected
    }
    // the pref store should remain unchanged
    Assert.assertEquals(expected, prefStore.getProperties(
      new ProgramId("myspace", "app", ProgramType.WORKFLOW, "prog")));

    // add the profile and disable it
    ProfileId profileId = new ProfileId("myspace", "userProfile");
    profileStore.saveProfile(profileId, Profile.NATIVE);
    profileStore.disableProfile(profileId);

    // this set call should fail since the profile is disabled
    try {
      prefStore.setProperties(new ProgramId("myspace", "app", ProgramType.WORKFLOW, "prog"), profileMap);
      Assert.fail();
    } catch (ProfileConflictException e) {
      // expected
    }
    // the pref store should remain unchanged
    Assert.assertEquals(expected, prefStore.getProperties(
      new ProgramId("myspace", "app", ProgramType.WORKFLOW, "prog")));

    // enable the profile
    profileStore.enableProfile(profileId);
    expected = profileMap;
    prefStore.setProperties(new ProgramId("myspace", "app", ProgramType.WORKFLOW, "prog"), profileMap);
    Map<String, String> properties = prefStore.getProperties(
      new ProgramId("myspace", "app", ProgramType.WORKFLOW, "prog"));
    Assert.assertEquals(expected, properties);

    prefStore.deleteProperties(new ProgramId("myspace", "app", ProgramType.WORKFLOW, "prog"));
    profileStore.disableProfile(profileId);
    profileStore.deleteProfile(profileId);
  }

  @Test
  public void testAddUserProfileToPreferencesInstanceLevel() throws Exception {
    PreferencesService prefStore = getInjector().getInstance(PreferencesService.class);

    // use profile in USER scope at instance level should fail
    try {
      prefStore.setProperties(Collections.singletonMap(SystemArguments.PROFILE_NAME, "userProfile"));
      Assert.fail();
    } catch (BadRequestException e) {
      // expected
    }

    try {
      prefStore.setProperties(Collections.singletonMap(SystemArguments.PROFILE_NAME, "USER:userProfile"));
      Assert.fail();
    } catch (BadRequestException e) {
      // expected
    }
  }

  @Test
  public void testProfileAssignment() throws Exception {
    PreferencesService preferencesService = getInjector().getInstance(PreferencesService.class);
    ProfileService profileService = getInjector().getInstance(ProfileService.class);
    ProfileId myProfile = NamespaceId.DEFAULT.profile("myProfile");
    profileService.saveProfile(myProfile, Profile.NATIVE);

    // add properties with profile information
    Map<String, String> prop = new HashMap<>();
    prop.put(SystemArguments.PROFILE_NAME, ProfileId.NATIVE.getScopedName());
    ApplicationId myApp = NamespaceId.DEFAULT.app("myApp");
    ProgramId myProgram = myApp.workflow("myProgram");
    preferencesService.setProperties(prop);
    preferencesService.setProperties(NamespaceId.DEFAULT, prop);
    preferencesService.setProperties(myApp, prop);
    preferencesService.setProperties(myProgram, prop);

    // the assignment should be there for these entities
    Set<EntityId> expected = new HashSet<>();
    expected.add(new InstanceId(""));
    expected.add(NamespaceId.DEFAULT);
    expected.add(myApp);
    expected.add(myProgram);
    Assert.assertEquals(expected, profileService.getProfileAssignments(ProfileId.NATIVE));

    // setting an empty property is actually deleting the assignment
    prop.clear();
    preferencesService.setProperties(myApp, prop);
    expected.remove(myApp);
    Assert.assertEquals(expected, profileService.getProfileAssignments(ProfileId.NATIVE));

    // set my program to use a different profile, should update both profiles
    prop.put(SystemArguments.PROFILE_NAME, myProfile.getScopedName());
    preferencesService.setProperties(myProgram, prop);
    expected.remove(myProgram);
    Assert.assertEquals(expected, profileService.getProfileAssignments(ProfileId.NATIVE));
    Assert.assertEquals(Collections.singleton(myProgram), profileService.getProfileAssignments(myProfile));

    // delete all preferences
    preferencesService.deleteProperties();
    preferencesService.deleteProperties(NamespaceId.DEFAULT);
    preferencesService.deleteProperties(myApp);
    preferencesService.deleteProperties(myProgram);
    Assert.assertEquals(Collections.emptySet(), profileService.getProfileAssignments(ProfileId.NATIVE));
    Assert.assertEquals(Collections.emptySet(), profileService.getProfileAssignments(myProfile));

    profileService.disableProfile(myProfile);
    profileService.deleteProfile(myProfile);
  }
}
