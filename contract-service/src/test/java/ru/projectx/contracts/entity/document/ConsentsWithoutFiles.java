package ru.projectx.contracts.entity.document;

public final class ConsentsWithoutFiles {

    private ConsentsWithoutFiles() {
    }

    public static PrintedForm bound(ConsentBinding owner) {
        PrintedForm form = new PrintedForm();
        form.consent = owner;
        return form;
    }
}
