package com.process.editor;

public interface IEditSave {
    void onSaveSuccess(String savePath);

    void onSaveFailed();
}
