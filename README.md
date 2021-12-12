[![Build Status](https://app.travis-ci.com/flowcommerce/dependency.svg?branch=main)](https://app.travis-ci.com/flowcommerce/dependency)

dependency
==========

It's hard to keep up with releases of all of the software we use. This
project is designed to keep track for you.

The core entity is the project - add a project from github, and the
dependency app will begin tracking all of your dependencies -
including the programming binaries you use. It also regularly
searches all of the resolvers your project uses to find all of the
available versions of each dependency and binary.

Whenever a newer version is found - that information is highlighted
for you as a task... and when you update your project, the dependency
app will automatically close the task picking up that your project has
been upgraded.
