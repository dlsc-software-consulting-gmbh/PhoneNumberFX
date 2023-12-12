
# PhoneNumberFX

[![Maven Central](https://img.shields.io/maven-central/v/com.dlsc.phonenumberfx/phonenumberfx)](https://search.maven.org/search?q=g:com.dlsc.phonenumberfx%20AND%20a:phonenumberfx)

This repository contains a text field control that is being used for entering valid phone numbers 
for any country in the world. The library has a dependency to [Google's _libphonenumber_ library](https://github.com/google/libphonenumber/),
which is rather big, hence we decided to distribute this control via its own project on GitHub
(as opposed to adding it to the GemsFX project). The text field can be configured to format
the number on a "value commit" event (focus lost, enter pressed) or constantly while the user is
typing.

A great tutorial for this control [can be found here](https://coderscratchpad.com/javafx-phone-number-input-field/).

The second control in this project is a specialized label that properly formats phone numbers set on
it via its `valueProperty()`. The label supports a "home country" so that phone numbers of the home country
will be shown in their national format, while phone numbers from other countries will be shown including 
their country code prefix.

The demo website of Google's library [can be found here](https://libphonenumber.appspot.com).
