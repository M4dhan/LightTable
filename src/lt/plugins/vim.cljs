(ns lt.plugins.vim
  (:require [lt.object :as object]
            [lt.objs.context :as ctx]
            [lt.util.load :as load]
            [lt.objs.editor.pool :as pool]
            [lt.objs.sidebar.command :as cmd :refer [command]]
            [lt.objs.editor :as editor]
            [lt.objs.settings :as settings]))

(def mode-tags {:all #{:editor.keys.vim.insert :editor.keys.normal
                       :editor.keys.vim.visual :editor.keys.vim.normal
                       :editor.keys.vim.ex }
                :normal-editor #{:editor.keys.normal}
                :normal #{:editor.keys.vim.normal}
                :insert #{:editor.keys.vim.insert :editor.keys.normal}
                :visual #{:editor.keys.vim.visual :editor.keys.vim.normal}
                :ex #{:editor.keys.vim.ex}})

(defn mode-change-listener [ed]
  (fn [mode]
    (object/raise ed :mode-change mode)))

(defn make-vim-editor [ed]
  (editor/set-options ed {:keyMap "vim"})
  (object/add-tags ed [:editor.keys.vim])
  (editor/on ed "vim.modeChange" (mode-change-listener ed))
  (object/raise ed :mode-change "normal"))

(defn make-normal-editor [ed]
  (editor/set-options ed {:keyMap "default"})
  (object/remove-tags ed [:editor.keys.vim])
  (editor/off ed "vim.modeChange" (mode-change-listener ed))
  (object/raise ed :mode-change "normal-editor"))

(object/behavior* ::should-add-vim?
                  :triggers #{:create}
                  :reaction (fn [this ed]
                              (when (settings/fetch :editor.vim)
                                (make-vim-editor ed))
                              ))

(object/behavior* ::mode-change
                  :triggers #{:mode-change}
                  :reaction (fn [this mode]
                              (object/remove-tags this (:all mode-tags))
                              (object/add-tags this (mode-tags (keyword mode)))))

(object/behavior* ::ex-command
                  :triggers #{:ex!}
                  :reaction (fn [this]
                              (cmd/show-filled "vim :" {:transient? true
                                                        :force? true})
                              ))

(object/behavior* ::find-bar-inactive
                  :triggers #{:inactive}
                  :reaction (fn [this]
                              (when (ctx/in? :find-bar.vim)
                                (cmd/exec! :hide-find)
                                (ctx/out! :find-bar.vim))))

(object/behavior* ::map-keys
                  :triggers #{:object.instant}
                  :reaction (fn [this ks]
                              (doseq [[k v] ks]
                                (js/CodeMirror.Vim.map k v))))

(object/behavior* ::activate-vim
                  :triggers #{:object.instant}
                  :reaction (fn [this]
                              (make-vim-editor this)))

(command {:command :vim
          :desc "Vim: Toggle vim mode"
          :exec (fn []
                  (let [cur (not (settings/fetch :editor.vim))
                        toggle-fn (if cur
                                    make-vim-editor
                                    make-normal-editor)]
                    (settings/store! :editor.vim cur)
                    (doseq [ed (object/by-tag :editor)]
                      (toggle-fn ed))))
                  })

(command {:command :vim-save
          :desc "Vim: :w"
          :exec (fn []
                  (cmd/exec! :save)
                  (cmd/exec! :focus-last-editor))})

(command {:command :vim-save-quit
          :desc "Vim: :wq"
          :exec (fn []
                  (let [cur (pool/last-active)]
                    (object/raise cur :save)
                    (object/raise cur :close)))})

(command {:command :vim-quit
          :desc "Vim: :q"
          :exec (fn []
                  (let [cur (pool/last-active)]
                    (object/raise cur :close)))})

(command {:command :vim.find
          :desc "Vim: find"
          :hidden true
          :exec (fn [rev?]
                  (ctx/in! :find-bar.vim)
                  (if rev?
                    (cmd/exec! :find.show true)
                    (cmd/exec! :find.show))
                  )})

(defn ex-command [cmd]
  (js/CodeMirror.Vim.defineEx (:name cmd) (:name cmd) (:func cmd)))


(load/js "core/node_modules/codemirror/vim.js" :sync)

(object/behavior* ::init
                  :triggers #{:init}
                  :reaction (fn [this]
                              (ex-command {:name "ltexec"
                                           :func (fn [cm info]
                                                   (apply cmd/exec! (-> (.-args info)
                                                                        (first)
                                                                        (keyword)) (next (.-args info))))})
                              ))
